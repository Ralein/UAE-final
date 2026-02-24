-- ============================================================
-- UAE PASS SP — Database Schema
-- Executed on startup when spring.sql.init.mode=always (staging)
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uaepass_uuid VARCHAR(255) UNIQUE NOT NULL,
    spuuid VARCHAR(255),
    idn TEXT,
    email VARCHAR(255),
    mobile VARCHAR(30),
    full_name_en VARCHAR(500),
    full_name_ar VARCHAR(500),
    first_name_en VARCHAR(255),
    last_name_en VARCHAR(255),
    nationality_en CHAR(3),
    gender VARCHAR(10),
    user_type VARCHAR(10),
    id_type VARCHAR(20),
    acr TEXT,
    linked_at TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS oauth_states (
    state VARCHAR(256) PRIMARY KEY,
    flow_type VARCHAR(50),
    redirect_after TEXT,
    user_id UUID REFERENCES users(id),
    expires_at TIMESTAMPTZ,
    used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    session_token VARCHAR(512) UNIQUE NOT NULL,
    uaepass_token_ref VARCHAR(256),
    token_expires TIMESTAMPTZ,
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    last_active TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS signing_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    signer_process_id VARCHAR(255) UNIQUE,
    user_id UUID REFERENCES users(id),
    signing_type VARCHAR(20),
    status VARCHAR(30),
    document_count INT DEFAULT 1,
    sign_identity_id VARCHAR(255),
    documents JSONB,
    finish_callback_url TEXT,
    callback_status VARCHAR(20),
    ltv_applied BOOLEAN DEFAULT FALSE,
    error_message TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    initiated_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS eseal_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requested_by UUID REFERENCES users(id),
    seal_type VARCHAR(10),
    status VARCHAR(20),
    input_key TEXT,
    output_key TEXT,
    request_id VARCHAR(255),
    error_message TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS face_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    transaction_ref VARCHAR(255),
    purpose TEXT,
    status VARCHAR(20),
    username_used VARCHAR(20),
    verified_uuid VARCHAR(255),
    uuid_match BOOLEAN,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    verified_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    action VARCHAR(100),
    entity_type VARCHAR(50),
    entity_id VARCHAR(255),
    ip_address INET,
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_uaepass_uuid ON users(uaepass_uuid);
CREATE INDEX IF NOT EXISTS idx_signing_jobs_user ON signing_jobs(user_id);
CREATE INDEX IF NOT EXISTS idx_signing_jobs_status ON signing_jobs(status);
CREATE INDEX IF NOT EXISTS idx_face_verifications_user ON face_verifications(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_user ON audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_created ON audit_log(created_at DESC);
