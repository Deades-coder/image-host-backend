package com.yang.imagehostbackend.listener;

import com.yang.imagehostbackend.service.PictureSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import javax.annotation.Resource;

/**
 * 图片变更监听器，用于同步数据到ES
 */
@Component
@Slf4j
public class PictureChangeListener {

    @Resource
    private PictureSearchService pictureSearchService;

    /**
     * 监听图片创建事件
     *
     * @param event 图片创建事件
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPictureCreated(PictureCreatedEvent event) {
        Long pictureId = event.getPictureId();
        log.info("接收到图片创建事件，pictureId={}", pictureId);
        try {
            pictureSearchService.syncPictureById(pictureId);
        } catch (Exception e) {
            log.error("同步新创建的图片到ES失败，pictureId={}", pictureId, e);
        }
    }

    /**
     * 监听图片更新事件
     *
     * @param event 图片更新事件
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPictureUpdated(PictureUpdatedEvent event) {
        Long pictureId = event.getPictureId();
        log.info("接收到图片更新事件，pictureId={}", pictureId);
        try {
            pictureSearchService.syncPictureById(pictureId);
        } catch (Exception e) {
            log.error("同步更新的图片到ES失败，pictureId={}", pictureId, e);
        }
    }

    /**
     * 监听图片删除事件
     *
     * @param event 图片删除事件
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPictureDeleted(PictureDeletedEvent event) {
        Long pictureId = event.getPictureId();
        log.info("接收到图片删除事件，pictureId={}", pictureId);
        try {
            pictureSearchService.deletePictureFromES(pictureId);
        } catch (Exception e) {
            log.error("从ES中删除图片失败，pictureId={}", pictureId, e);
        }
    }

    /**
     * 图片创建事件
     */
    public static class PictureCreatedEvent {
        private final Long pictureId;

        public PictureCreatedEvent(Long pictureId) {
            this.pictureId = pictureId;
        }

        public Long getPictureId() {
            return pictureId;
        }
    }

    /**
     * 图片更新事件
     */
    public static class PictureUpdatedEvent {
        private final Long pictureId;

        public PictureUpdatedEvent(Long pictureId) {
            this.pictureId = pictureId;
        }

        public Long getPictureId() {
            return pictureId;
        }
    }

    /**
     * 图片删除事件
     */
    public static class PictureDeletedEvent {
        private final Long pictureId;

        public PictureDeletedEvent(Long pictureId) {
            this.pictureId = pictureId;
        }

        public Long getPictureId() {
            return pictureId;
        }
    }
} 