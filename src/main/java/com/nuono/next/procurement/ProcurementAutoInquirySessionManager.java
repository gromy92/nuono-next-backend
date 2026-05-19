package com.nuono.next.procurement;

import com.nuono.next.infrastructure.mapper.ProcurementMapper;
import com.nuono.next.procurement.ProcurementAutoInquiryWorkbenchView.AutoInquirySessionView;
import com.nuono.next.procurement.ProcurementAutoInquiryWorkbenchView.AutoInquiryTaskView;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local-db")
public class ProcurementAutoInquirySessionManager {

    private final ProcurementMapper procurementMapper;

    public ProcurementAutoInquirySessionManager(ProcurementMapper procurementMapper) {
        this.procurementMapper = procurementMapper;
    }

    @Transactional
    public SessionLease reserveReadySession(Long taskId, Long operatorUserId) {
        reclaimReusableSessions(operatorUserId);
        AutoInquirySessionView candidateSession = procurementMapper.selectFirstAvailableAutoInquirySession(
                ProcurementAutoInquiryLifecycle.PLATFORM_1688
        );
        if (candidateSession == null || candidateSession.getId() == null) {
            return SessionLease.unavailable("NO_READY_SESSION", "当前没有可用的 1688 托管会话，需先补齐服务端会话。");
        }

        int updatedRows = procurementMapper.reserveAutoInquirySession(
                candidateSession.getId(),
                taskId,
                operatorUserId
        );
        if (updatedRows <= 0) {
            return SessionLease.unavailable("SESSION_BUSY", "目标托管会话已被其他任务占用，请稍后重试。");
        }

        AutoInquirySessionView leasedSession = procurementMapper.selectAutoInquirySession(candidateSession.getId());
        if (leasedSession == null) {
            return SessionLease.unavailable("SESSION_LOST", "托管会话已被预留，但回读时未找到对应记录。");
        }
        return SessionLease.success(leasedSession);
    }

    @Transactional
    public void releaseSession(Long sessionId, Long operatorUserId, String note) {
        if (sessionId == null) {
            return;
        }
        procurementMapper.releaseAutoInquirySession(sessionId, operatorUserId, note);
    }

    @Transactional
    public void reclaimReusableSessions(Long operatorUserId) {
        List<AutoInquirySessionView> sessions = procurementMapper.listAutoInquirySessions(
                ProcurementAutoInquiryLifecycle.PLATFORM_1688
        );
        for (AutoInquirySessionView session : sessions) {
            if (session == null
                    || session.getId() == null
                    || !ProcurementAutoInquiryLifecycle.SESSION_BUSY.equals(
                            ProcurementAutoInquiryLifecycle.upper(session.getStatus())
                    )) {
                continue;
            }

            Long leasedTaskId = session.getLeasedTaskId();
            if (leasedTaskId == null) {
                procurementMapper.releaseAutoInquirySession(
                        session.getId(),
                        operatorUserId,
                        "发现空占用后自动回收"
                );
                continue;
            }

            AutoInquiryTaskView leasedTask = procurementMapper.selectAutoInquiryTask(leasedTaskId);
            if (leasedTask == null || shouldRecycleSession(leasedTask)) {
                procurementMapper.releaseAutoInquirySession(
                        session.getId(),
                        operatorUserId,
                        "上一轮发送链路已结束，自动回收托管会话"
                );
            }
        }
    }

    private boolean shouldRecycleSession(AutoInquiryTaskView leasedTask) {
        String status = ProcurementAutoInquiryLifecycle.upper(leasedTask.getStatus());
        if (ProcurementAutoInquiryLifecycle.STATUS_HANDOFF.equals(status)
                || ProcurementAutoInquiryLifecycle.STATUS_CLOSED.equals(status)) {
            return true;
        }
        return ProcurementAutoInquiryLifecycle.STATUS_SENT.equals(status)
                && ProcurementAutoInquiryLifecycle.STAGE_SEND_CONFIRMED.equals(
                        ProcurementAutoInquiryLifecycle.upper(leasedTask.getExecutionStage())
                );
    }

    public static class SessionLease {

        private final boolean leased;

        private final AutoInquirySessionView session;

        private final String failureCode;

        private final String failureMessage;

        private SessionLease(
                boolean leased,
                AutoInquirySessionView session,
                String failureCode,
                String failureMessage
        ) {
            this.leased = leased;
            this.session = session;
            this.failureCode = failureCode;
            this.failureMessage = failureMessage;
        }

        public static SessionLease success(AutoInquirySessionView session) {
            return new SessionLease(true, session, null, null);
        }

        public static SessionLease unavailable(String failureCode, String failureMessage) {
            return new SessionLease(false, null, failureCode, failureMessage);
        }

        public boolean isLeased() {
            return leased;
        }

        public AutoInquirySessionView getSession() {
            return session;
        }

        public String getFailureCode() {
            return failureCode;
        }

        public String getFailureMessage() {
            return failureMessage;
        }
    }
}
