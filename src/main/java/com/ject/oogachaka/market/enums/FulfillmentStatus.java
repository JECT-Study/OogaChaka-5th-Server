package com.ject.oogachaka.market.enums;

/**
 * 마켓 주문 상품의 지급 상태
 */
public enum FulfillmentStatus {
    /** 지급 대기 */
    PENDING,

    /** 지급 처리 중 */
    PROCESSING,

    /** 지급 완료 */
    COMPLETED,

    /** 지급 실패 */
    FAILED
}
