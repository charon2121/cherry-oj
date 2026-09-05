ALTER TABLE user_login_session
    DROP CHECK ck_user_login_session_time_order;

ALTER TABLE user_login_session
    MODIFY idle_expires_at DATETIME(6) NULL;

ALTER TABLE user_login_session
    ADD CONSTRAINT ck_user_login_session_time_order CHECK (
        last_used_at >= created_at
        AND absolute_expires_at > created_at
    );
