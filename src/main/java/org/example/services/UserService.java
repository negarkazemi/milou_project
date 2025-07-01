package org.example.services;

import org.example.framework.SingletonSessionFactory;

import org.example.model.User;


public class UserService {

    public User register(String name, String email, String password) {
        String normalizedEmail = normalizeEmail(email);

        if (password.length() < 8) {
            System.err.println("Weak password");
            return null;
        }

        User existingUser = SingletonSessionFactory.get()
                .fromTransaction(session -> session.createNativeQuery(
                                "select * from users WHERE email = :email", User.class)
                        .setParameter("email", normalizedEmail)
                        .uniqueResult());

        if (existingUser != null) {
            System.err.println("An account with this email already exists.");
            return null;
        }

        User user = new User(name, normalizedEmail, password);
        SingletonSessionFactory.get().inTransaction(session -> session.persist(user));

        System.out.println("Your new account is created. Go ahead and login!");
        return user;
    }

    public User login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);

        User user = SingletonSessionFactory.get()
                .fromTransaction(session -> session.createNativeQuery(
                                "select * from users where email = :email", User.class)
                        .setParameter("email", normalizedEmail)
                        .uniqueResult());

        if (user == null || !user.checkPassword(password)) {
            System.out.println("Invalid email or password.");
            return null;
        }

        System.out.println("Welcome back, " + user.getName() + "!");
        return user;
    }

    private String normalizeEmail(String email) {
        return email.contains("@") ? email : email + "@milou.com";
    }

    public User findByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);

        return SingletonSessionFactory.get()
                .fromTransaction(session -> session.createNativeQuery(
                                "select * from users where email = :email", User.class)
                        .setParameter("email", normalizedEmail)
                        .uniqueResult()
                );
    }


}
