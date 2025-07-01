create database milou_project;
use milou_project;

create table users (
    id bigint primary key auto_increment,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL
);

create table emails (
    id bigint primary key auto_increment,
    sender_id bigint not null,
    subject varchar(255) not null,
    body text not null,
    sent_at datetime default CURRENT_TIMESTAMP,
    code varchar(255) unique not null,
    foreign key (sender_id) references users(id)
);

create table email_recipients (
    id bigint auto_increment primary key,
    email_id bigint not null,
    recipient_id bigint not null,
    is_read boolean default 0,
    foreign key (email_id) references emails(id),
    foreign key (recipient_id) references users(id)
);

select * from users;
