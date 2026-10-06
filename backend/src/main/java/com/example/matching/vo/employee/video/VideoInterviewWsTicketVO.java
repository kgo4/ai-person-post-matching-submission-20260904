package com.example.matching.vo.employee.video;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Short-lived ticket used for native browser WebSocket authentication.
 */
@Data
@AllArgsConstructor
public class VideoInterviewWsTicketVO {

    private String ticket;

    private Long expiresAt;
}
