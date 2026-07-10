package com.sky.dto;



import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatisticsDTO implements Serializable {

    private LocalDateTime begintime;

    private LocalDateTime endtime;

    private Long shopId;

    private Integer status;
}
