-- 图片表
CREATE TABLE IF NOT EXISTS picture (
                                       id BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
                                       url VARCHAR(191) NOT NULL COMMENT '图片 url',
                                       name VARCHAR(128) NOT NULL COMMENT '图片名称',
                                       introduction VARCHAR(191) NULL COMMENT '简介',
                                       category VARCHAR(64) NULL COMMENT '分类',
                                       tags VARCHAR(191) NULL COMMENT '标签（JSON 数组）',  -- 缩小长度
                                       picSize BIGINT NULL COMMENT '图片体积',
                                       picWidth INT NULL COMMENT '图片宽度',
                                       picHeight INT NULL COMMENT '图片高度',
                                       picScale DOUBLE NULL COMMENT '图片宽高比例',
                                       picFormat VARCHAR(32) NULL COMMENT '图片格式',
                                       userId BIGINT NOT NULL COMMENT '创建用户 id',
                                       createTime DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
                                       editTime DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '编辑时间',
                                       updateTime DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                       isDelete TINYINT DEFAULT 0 NOT NULL COMMENT '是否删除',
                                       INDEX idx_name (name),
                                       INDEX idx_introduction (introduction(191)),  -- 前缀索引
                                       INDEX idx_category (category),
                                       INDEX idx_tags (tags(191)),                 -- 前缀索引
                                       INDEX idx_userId (userId)
) COMMENT '图片' COLLATE = utf8mb4_unicode_ci;

ALTER TABLE picture
    -- 添加新列
    ADD COLUMN reviewStatus INT DEFAULT 0 NOT NULL COMMENT '审核状态：0-待审核; 1-通过; 2-拒绝',
    ADD COLUMN reviewMessage VARCHAR(512) NULL COMMENT '审核信息',
    ADD COLUMN reviewerId BIGINT NULL COMMENT '审核人 ID',
    ADD COLUMN reviewTime DATETIME NULL COMMENT '审核时间';

-- 创建基于 reviewStatus 列的索引
CREATE INDEX idx_reviewStatus ON picture (reviewStatus);


ALTER TABLE picture
    -- 添加新列
    ADD COLUMN thumbnailUrl varchar(512) NULL COMMENT '缩略图 url';
-- 添加新列
ALTER TABLE picture
    ADD COLUMN spaceId  bigint  null comment '空间 id（为空表示公共空间）';

-- 创建索引
CREATE INDEX idx_spaceId ON picture (spaceId);
