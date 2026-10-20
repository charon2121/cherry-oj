-- 登录会话的标识统一叫 sessionId（docs/protocols/service-identity.md §5）；库里存的是它的 SHA-256。
ALTER TABLE user_login_session
    RENAME COLUMN grant_hash TO session_id_hash;

ALTER TABLE user_login_session
    RENAME INDEX uq_user_login_session_grant_hash TO uq_user_login_session_session_id_hash;
