create table if not exists chats
(
    id            bigint not null unique,
    api_url       text not null,
    api_session   text not null
);

create index idx_chats on chats(id);

create table if not exists chats_preferences
(
    id                      serial primary key,
    chat_id                 bigint not null unique references chats(id),
    preferred_account_id    bigint not null default -1,
    gpt_mode                int not null default 0,
    tips_showed             boolean not null default true,
    auto_accept_transactions boolean not null default false,
    hide_amounts            boolean not null default false,
    notification_uuid       uuid
);

create index idx_chats_preferences on chats_preferences(id, chat_id);
