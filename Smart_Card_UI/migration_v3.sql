-- ============================================
-- Database Migration Script V3
-- Hospital Card System - Luồng mới với K_master và PIN_admin_reset derive động
-- ============================================
-- 
-- Thay đổi chính:
-- 1. Cập nhật user_cards: Xóa pk_user, admin_pin_encrypted; Thêm status, expires_at, backup
-- 2. Tạo bảng patients: Quản lý thông tin bệnh nhân riêng
-- 3. Tạo bảng card_status_history: Track lịch sử thay đổi trạng thái
-- 4. Cập nhật system_keys: Chỉ lưu metadata (K_master lưu trong .env)
--
-- ============================================

-- ============================================
-- BƯỚC 0: XÓA CÁC BẢNG V2 CŨ (nếu tồn tại)
-- ============================================

-- Xóa các bảng V2 cũ không còn dùng
DROP TABLE IF EXISTS admin_cards CASCADE;
DROP TABLE IF EXISTS bhyt_info CASCADE;

-- Đổi tên bảng admin_users → admin (nếu tồn tại)
DO $$ 
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'admin_users') THEN
        ALTER TABLE admin_users RENAME TO admin;
        RAISE NOTICE 'Đã đổi tên bảng admin_users → admin';
    END IF;
END $$;

-- ============================================
-- BƯỚC 1: TẠO BẢNG MỚI
-- ============================================

-- Bảng patients: Quản lý thông tin bệnh nhân
CREATE TABLE IF NOT EXISTS patients (
    patient_id VARCHAR(50) PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    date_of_birth DATE,
    gender VARCHAR(10),  -- M, F, Other
    phone VARCHAR(20),
    email VARCHAR(100),
    address TEXT,
    id_card_number VARCHAR(20),  -- CMND/CCCD
    insurance_number VARCHAR(50),  -- Số BHYT
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    notes TEXT
);

-- Bảng admin: Quản lý admin users (username/password)
-- Nếu bảng admin_users đã tồn tại, đã được đổi tên ở trên
CREATE TABLE IF NOT EXISTS admin (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    salt VARCHAR(255),
    role VARCHAR(20) DEFAULT 'admin',
    full_name VARCHAR(100),
    email VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    last_login TIMESTAMP,
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Bảng card_status_history: Lịch sử thay đổi trạng thái thẻ
CREATE TABLE IF NOT EXISTS card_status_history (
    id SERIAL PRIMARY KEY,
    card_id BYTEA NOT NULL,
    old_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    changed_by_admin_id INTEGER REFERENCES admin(id),
    reason TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- ============================================
-- BƯỚC 2: CẬP NHẬT BẢNG user_cards
-- ============================================

-- Xóa các cột cũ (nếu tồn tại)
ALTER TABLE user_cards 
    DROP COLUMN IF EXISTS pk_user,
    DROP COLUMN IF EXISTS admin_pin_encrypted,
    DROP COLUMN IF EXISTS admin_pin_encryption_key_version;

-- Thêm các cột mới
ALTER TABLE user_cards
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS encrypted_patient_data_backup TEXT,
    ADD COLUMN IF NOT EXISTS backup_created_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS issued_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS issued_by_admin_id INTEGER REFERENCES admin(id),
    ADD COLUMN IF NOT EXISTS notes TEXT;

-- Cập nhật foreign key cho patient_id
ALTER TABLE user_cards
    ADD CONSTRAINT fk_user_cards_patient_id 
    FOREIGN KEY (patient_id) REFERENCES patients(patient_id) ON DELETE SET NULL;

-- ============================================
-- BƯỚC 3: CẬP NHẬT BẢNG system_keys (nếu cần)
-- ============================================

-- Xóa cột key_encrypted (vì K_master lưu trong .env, không lưu DB)
ALTER TABLE system_keys
    DROP COLUMN IF EXISTS key_encrypted,
    DROP COLUMN IF EXISTS hsm_key_id;

-- Thêm cột last_rotated_at (nếu chưa có)
ALTER TABLE system_keys
    ADD COLUMN IF NOT EXISTS last_rotated_at TIMESTAMP;

-- ============================================
-- BƯỚC 4: TẠO INDEXES
-- ============================================

-- Indexes cho user_cards
CREATE INDEX IF NOT EXISTS idx_user_cards_patient_id ON user_cards(patient_id);
CREATE INDEX IF NOT EXISTS idx_user_cards_status ON user_cards(status);
CREATE INDEX IF NOT EXISTS idx_user_cards_expires_at ON user_cards(expires_at);
CREATE INDEX IF NOT EXISTS idx_user_cards_issued_at ON user_cards(issued_at);
CREATE INDEX IF NOT EXISTS idx_user_cards_created_at ON user_cards(created_at);

-- Indexes cho patients
CREATE INDEX IF NOT EXISTS idx_patients_full_name ON patients(full_name);
CREATE INDEX IF NOT EXISTS idx_patients_id_card_number ON patients(id_card_number);
CREATE INDEX IF NOT EXISTS idx_patients_insurance_number ON patients(insurance_number);

-- Indexes cho card_status_history
CREATE INDEX IF NOT EXISTS idx_card_status_history_card_id ON card_status_history(card_id);
CREATE INDEX IF NOT EXISTS idx_card_status_history_created_at ON card_status_history(created_at);

-- Indexes cho admin
CREATE INDEX IF NOT EXISTS idx_admin_username ON admin(username);
CREATE INDEX IF NOT EXISTS idx_admin_is_active ON admin(is_active);

-- Indexes cho admin_audit_log
CREATE INDEX IF NOT EXISTS idx_admin_audit_log_admin_user_id ON admin_audit_log(admin_user_id);
CREATE INDEX IF NOT EXISTS idx_admin_audit_log_action ON admin_audit_log(action);
CREATE INDEX IF NOT EXISTS idx_admin_audit_log_card_id ON admin_audit_log(card_id);
CREATE INDEX IF NOT EXISTS idx_admin_audit_log_created_at ON admin_audit_log(created_at);

-- Indexes cho system_logs
CREATE INDEX IF NOT EXISTS idx_system_logs_card_id ON system_logs(card_id);
CREATE INDEX IF NOT EXISTS idx_system_logs_action ON system_logs(action);
CREATE INDEX IF NOT EXISTS idx_system_logs_created_at ON system_logs(created_at);

-- ============================================
-- BƯỚC 5: TẠO STORED PROCEDURE
-- ============================================

-- Stored Procedure: Cập nhật trạng thái thẻ (có audit trail)
CREATE OR REPLACE FUNCTION update_card_status(
    p_card_id BYTEA,
    p_new_status VARCHAR(20),
    p_admin_id INTEGER,
    p_reason TEXT DEFAULT NULL
) RETURNS VOID AS $$
DECLARE
    v_old_status VARCHAR(20);
BEGIN
    -- Lấy trạng thái cũ
    SELECT status INTO v_old_status FROM user_cards WHERE card_id = p_card_id;
    
    -- Cập nhật trạng thái mới
    UPDATE user_cards 
    SET status = p_new_status,
        last_updated_at = NOW()
    WHERE card_id = p_card_id;
    
    -- Ghi vào lịch sử
    INSERT INTO card_status_history (card_id, old_status, new_status, changed_by_admin_id, reason)
    VALUES (p_card_id, v_old_status, p_new_status, p_admin_id, p_reason);
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- BƯỚC 6: MIGRATE DỮ LIỆU CŨ (nếu có)
-- ============================================

-- Nếu có dữ liệu cũ trong user_cards với status NULL, set mặc định
UPDATE user_cards 
SET status = 'ACTIVE' 
WHERE status IS NULL AND patient_id IS NOT NULL;

UPDATE user_cards 
SET status = 'PENDING' 
WHERE status IS NULL AND patient_id IS NULL;

-- ============================================
-- HOÀN TẤT
-- ============================================

-- Kiểm tra các bảng đã được tạo
SELECT 'Migration V3 completed successfully!' AS message;

