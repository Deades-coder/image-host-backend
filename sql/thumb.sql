-- 点赞表
CREATE TABLE IF NOT EXISTS `thumb`
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '点赞ID',
    userId     BIGINT NOT NULL COMMENT '用户ID',
    pictureId  BIGINT NOT NULL COMMENT '被点赞对象ID',
    createTime DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间'
);

-- 创建联合唯一索引确保一个用户对某个对象只能点赞一次
create unique index idx_userId_target on `thumb` (userId, pictureId);
