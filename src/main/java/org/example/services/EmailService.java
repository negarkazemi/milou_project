package org.example.services;

import org.example.framework.SingletonSessionFactory;
import org.example.model.Email;
import org.example.model.User;
import org.hibernate.Session;

import java.time.LocalDate;
import java.util.*;

import static org.example.services.UserService.normalizeEmail;

public class EmailService {

    private UserService userService = new UserService();

    public void sendEmail(User sender, String recipientsRaw, String subject, String body) {
        String[] parts = recipientsRaw.split("[,\\s]+");
        List<User> recipients = new ArrayList<>();

        for (String part : parts) {
            String email = part.trim();
            if (!email.contains("@")) {
                email += "@milou.com";
            }

            User recipient = userService.findByEmail(email);
            if (recipient != null) {
                recipients.add(recipient);
            } else {
                System.err.println("User not found: " + email + "\n");
            }
        }

        if (recipients.isEmpty()) {
            System.err.println("No valid recipients. Email not sent.\n");
            return;
        }

        SingletonSessionFactory.get().inTransaction(session -> {
            String code = generateUniqueCode(session);

            Email email = new Email(subject, body, sender, recipients, code);
            session.persist(email);

            System.out.println("Successfully sent your email.");
            System.out.println("Code: " + code);
        });
    }

    public List<Email> getAllReceivedEmails(User user) {
        return SingletonSessionFactory.get().fromTransaction(session ->
                session.createNativeQuery("""
                                    select e.* from emails e
                                    join email_recipients er on er.email_id = e.id
                                    where er.recipient_id = :userId
                                    order by e.sent_at DESC
                                """, Email.class)
                        .setParameter("userId", user.getId())
                        .getResultList()
        );
    }

    public List<Email> getUnreadEmails(User user) {
        return SingletonSessionFactory.get().fromTransaction(session ->
                session.createNativeQuery("""
                                    select e.*
                                    from emails e
                                    join email_recipients er on e.id = er.email_id
                                    where er.recipient_id = :userId
                                      and er.is_read = false
                                    order by e.sent_at DESC
                                """, Email.class)
                        .setParameter("userId", user.getId())
                        .getResultList()
        );
    }

    public List<Object[]> getSentEmails(User sender) {
        return SingletonSessionFactory.get().fromTransaction(session ->
                session.createNativeQuery("""
                                    select e.subject, e.code, GROUP_CONCAT(DISTINCT r.email SEPARATOR ', ') as recipients
                                    from emails e
                                    join email_recipients er on er.email_id = e.id
                                    join users r on er.recipient_id = r.id
                                    where e.sender_id = :senderId
                                    group by e.id
                                    order by e.sent_at DESC
                                """)
                        .setParameter("senderId", sender.getId())
                        .list()
        );
    }

    public void readEmailByCode(User user, String code) {
        SingletonSessionFactory.get().inTransaction(session -> {
            Email email = session.createNativeQuery("""
                                select e.*
                                from emails e
                                where e.code = :code
                            """, Email.class)
                    .setParameter("code", code)
                    .uniqueResult();

            if (email == null) {
                System.err.println("No email found with this code.");
                return;
            }

            boolean isSender = email.getSender().getId().equals(user.getId());

            boolean isRecipient = session.createNativeQuery("""
                                select 1
                                from email_recipients
                                where email_id = :emailId
                                  and recipient_id = :userId
                            """)
                    .setParameter("emailId", email.getId())
                    .setParameter("userId", user.getId())
                    .uniqueResult() != null;

            if (!isSender && !isRecipient) {
                System.err.println("You cannot read this email.");
                return;
            }


            System.out.println("Code: " + email.getCode());

            List<String> recipientEmails = session.createNativeQuery("""
                                select u.email
                                from users u
                                join email_recipients er on er.recipient_id = u.id
                                where er.email_id = :emailId
                            """)
                    .setParameter("emailId", email.getId())
                    .getResultList();

            System.out.println("Recipient(s): " + String.join(", ", recipientEmails));
            System.out.println("Subject: " + email.getSubject());
            System.out.println("Date: " + email.getSentAt().toLocalDate());
            System.out.println();
            System.out.println(email.getBody());

            if (isRecipient) {
                session.createNativeQuery("""
                                    update email_recipients
                                    set is_read = true
                                    where email_id = :emailId
                                      and recipient_id = :userId
                                """)
                        .setParameter("emailId", email.getId())
                        .setParameter("userId", user.getId())
                        .executeUpdate();
            }
        });
    }


    public void replyToEmail(User replier, String originalCode, String replyBody) {
        SingletonSessionFactory.get().inTransaction(session -> {

            Email originalEmail = session.createNativeQuery(
                            "select * from emails where code = :code", Email.class)
                    .setParameter("code", originalCode)
                    .uniqueResult();

            if (originalEmail == null) {
                System.err.println("Email not found.\n");
                return;
            }

            Long userId = replier.getId();
            Long emailId = originalEmail.getId();

            boolean isSender = session.createNativeQuery(""" 
                            select 1 from emails where id = :emailId AND sender_id = :userId
                            """)
                    .setParameter("emailId", emailId)
                    .setParameter("userId", userId)
                    .uniqueResult() != null;

            boolean isRecipient = session.createNativeQuery("""
                                     select 1 from email_recipients where email_id = :emailId AND recipient_id = :userId
                                    """)
                            .setParameter("emailId", emailId)
                            .setParameter("userId", userId)
                            .uniqueResult() != null;

            if (!isSender && !isRecipient) {
                System.err.println("You cannot reply to this email.\n");
                return;
            }


            List<User> originalRecipients = session.createNativeQuery(
                            """
                                    select u.* from users u join email_recipients er on u.id = er.recipient_id 
                                    where er.email_id = :emailId""",
                            User.class)
                    .setParameter("emailId", emailId)
                    .getResultList();

            List<User> recipients = new ArrayList<>();
            if (!originalEmail.getSender().getId().equals(userId)) {
                recipients.add(originalEmail.getSender());
            }
            for (User u : originalRecipients) {
                if (!u.getId().equals(userId) &&
                        recipients.stream().noneMatch(r -> r.getId().equals(u.getId()))) {
                    recipients.add(u);
                }
            }

            if (recipients.isEmpty()) {
                System.out.println("No one to reply to.");
                return;
            }

            String replySubject = "[Re] " + originalEmail.getSubject();

            String newCode;
            do {
                newCode = generateUniqueCode(session);
            } while (session.createNativeQuery(
                            "select 1 from emails where code = :code")
                    .setParameter("code", newCode)
                    .uniqueResult() != null);

            Email replyEmail = new Email(replySubject, replyBody, replier, recipients, newCode);
            session.persist(replyEmail);


            System.out.println("Code: " + newCode);
            System.out.print("Recipient(s): ");
            List<String> emails = new ArrayList<>();
            for (User u : recipients) {
                emails.add(u.getEmail());
            }
            System.out.println(String.join(", ", emails));
            System.out.println("Subject: " + replySubject);
            System.out.println("Date: " + LocalDate.now());
            System.out.println();
            System.out.println(replyBody);

            System.out.println("Successfully sent your reply to email " + originalCode + ".");
            System.out.println("Code: " + newCode);
        });
    }


    public void forwardEmail(User forwarder, String originalCode, List<String> recipientEmails) {
        SingletonSessionFactory.get().inTransaction(session -> {
            Email originalEmail = session.createNativeQuery(
                            "select * from emails where code = :code", Email.class)
                    .setParameter("code", originalCode)
                    .uniqueResult();

            if (originalEmail == null) {
                System.err.println("Original email not found.\n");
                return;
            }

            Long forwarderUserId = forwarder.getId();
            Long originalEmailId = originalEmail.getId();

            for (int i = 0; i < recipientEmails.size(); i++) {
                recipientEmails.set(i, normalizeEmail(recipientEmails.get(i)));
            }

            boolean allowed = session.createNativeQuery("""
                                select 1 from emails e
                                where e.id = :emailId and (e.sender_id = :userId or exists (
                                    select 1 from email_recipients r
                                    where r.email_id = e.id and r.recipient_id = :userId
                                ))
                            """)
                    .setParameter("emailId", originalEmailId)
                    .setParameter("userId", forwarderUserId)
                    .uniqueResult() != null;

            if (!allowed) {
                System.out.println("You cannot forward this email.\n");
                return;
            }

            List<User> newRecipients = session.createNativeQuery(
                            "select * from users WHERE email in (:emails)", User.class)
                    .setParameter("emails", recipientEmails)
                    .getResultList();

            if (newRecipients.isEmpty()) {
                System.err.println("No valid recipients found.");
                return;
            }

            String subject = "[Fw] " + originalEmail.getSubject();
            String body = originalEmail.getBody();

            String code;
            do {
                code = generateUniqueCode(session);
            } while (session.createNativeQuery("select 1 from emails WHERE code = :code")
                    .setParameter("code", code)
                    .uniqueResult() != null);

            Email forwarded = new Email(subject, body, forwarder, newRecipients, code);
            session.persist(forwarded);

            System.out.println("Code: " + code);
            List<String> emails = new ArrayList<>();
            for (User u : newRecipients) {
                emails.add(u.getEmail());
            }
            System.out.println("Recipient(s): " + String.join(", ", emails));
            System.out.println("Subject: " + subject);
            System.out.println("Date: " + forwarded.getSentAt().toLocalDate());
            System.out.println();
            System.out.println(body);
            System.out.println();

            System.out.println("Successfully forwarded your email.");
            System.out.println("Code: " + code);
        });
    }


    public String generateUniqueCode(Session session) {
        String code;
        boolean exists;

        do {
            code = generateRandomCode();
            exists = session.createQuery("select 1 from Email e where e.code = :code")
                    .setParameter("code", code)
                    .uniqueResult() != null;
        } while (exists);

        return code;
    }

    private String generateRandomCode() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder builder = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            builder.append(chars.charAt(random.nextInt(chars.length())));
        }
        return builder.toString();
    }


    public static void printEmailList(List<Email> emails) {
        for (Email email : emails) {
            String senderEmail = email.getSender().getEmail();
            String subject = email.getSubject();
            String code = email.getCode();
            System.out.println("+ " + senderEmail + " - " + subject + " (" + code + ")");
        }

    }

}