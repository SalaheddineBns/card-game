package com.cardgame.invitation;

/** D7: PENDING → ACCEPTED (game started) | DECLINED (anyone declined) | EXPIRED (60 s). */
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    EXPIRED
}
