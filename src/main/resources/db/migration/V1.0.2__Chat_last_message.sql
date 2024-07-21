alter table chats
    add column last_message integer not null default -1;