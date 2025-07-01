package org.example;

import org.example.model.*;
import org.example.services.*;

import java.util.*;

import static org.example.services.EmailService.printEmailList;

public class Main {
    private static EmailService emailService = new EmailService();
    private static User currentUser;
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        UserService userService = new UserService();


        while (true) {
            System.out.print("\n[L]ogin, [S]ign up: ");
            String command = scanner.nextLine().trim().toLowerCase();

            if (command.equals("l") || command.equals("login")) {
                handleLogin(scanner, userService);
            } else if (command.equals("s") || command.equals("sign up") || command.equals("signup")) {
                handleSignup(scanner, userService);
            } else {
                System.out.println("Invalid command. Try again.");
            }
        }
    }

    private static void handleLogin(Scanner scanner, UserService userService) {
        System.out.print("Email: ");
        String email = scanner.nextLine().trim();

        System.out.print("Password: ");
        String password = scanner.nextLine().trim();

        User user = userService.login(email, password);

        if (user != null) {
            currentUser = user;

            List<Email> unreadEmails = emailService.getUnreadEmails(currentUser);
            System.out.println("Unread Emails:\n" +
                    "\n" +
                    unreadEmails.size() + 1 + " unread emails:");
            printEmailList(unreadEmails);

            while (true) {
                System.out.print("[S]end, [V]iew, [R]eply, [F]orward: ");
                String cmd = scanner.nextLine().trim().toLowerCase();

                switch (cmd) {
                    case "s":
                    case "send":
                        System.out.print("Recipient(s): ");
                        String recipients = scanner.nextLine().trim();

                        System.out.print("Subject: ");
                        String subject = scanner.nextLine().trim();

                        System.out.print("Body: ");
                        String body = scanner.nextLine().trim();

                        emailService.sendEmail(user, recipients, subject, body);
                        break;

                    case "v":
                    case "view":
                        System.out.print("[A]ll emails, [U]nread emails, [S]ent emails, Read by [C]ode: ");
                        String readCmd = scanner.nextLine().trim().toLowerCase();


                        switch (readCmd) {
                        case "a":
                        case "all":
                            System.out.println("All Emails:");
                            List<Email> allEmails = emailService.getAllReceivedEmails(currentUser);
                            printEmailList(allEmails);
                            break;

                        case "u":
                        case "unread":
                            System.out.println("Unread Emails:");
                            List<Email> unreadEmail = emailService.getUnreadEmails(currentUser);
                            printEmailList(unreadEmail);
                            break;

                        case "s":
                        case "sent":
                            List<Object[]> emails = emailService.getSentEmails(currentUser);
                            System.out.println("Sent Emails:");
                            for (Object[] row : emails) {
                                String emailSubject = (String) row[0];
                                String code = (String) row[1];
                                String emailRecipients = (String) row[2];

                                System.out.println("+ " + emailRecipients + " - " + emailSubject + " (" + code + ")");
                            }
                            break;

                        case "c":
                        case "code":
                            System.out.println("Code:");
                            String code = scanner.nextLine().trim().toLowerCase();
                            emailService.readEmailByCode(currentUser, code);
                            break;

                        }
                            break;

                    case "r":
                    case "reply":
                        System.out.println("Code: ");
                        String code = scanner.nextLine().trim().toLowerCase();
                        System.out.print("Body: ");
                        String replyBody = scanner.nextLine().trim().toLowerCase();
                        emailService.replyToEmail(currentUser, code, replyBody);
                        break;

                    case "f":
                    case "forward":
                        System.out.print("Code: ");
                        String originalCode = scanner.nextLine().trim();

                        System.out.print("Recipient(s): ");
                        String recipientLine = scanner.nextLine().trim();

                        String[] forwardRecipients = recipientLine.split("[,\\s]+");
                        List<String> recipientEmails = List.of(forwardRecipients);

                        emailService.forwardEmail(currentUser, originalCode, recipientEmails);
                        break;

                    default:
                        System.out.println("Invalid command.");
                }
            }
        }
    }

    private static void handleSignup(Scanner scanner, UserService userService) {
        while (true) {
            System.out.print("Name: ");
            String name = scanner.nextLine().trim();

            System.out.print("Email: ");
            String email = scanner.nextLine().trim();

            System.out.print("Password: ");
            String password = scanner.nextLine().trim();

            User user = userService.register(name, email, password);
            if (user != null) {
                break;
            }

            System.out.println("Please try signing up again.\n");
        }
    }
}

