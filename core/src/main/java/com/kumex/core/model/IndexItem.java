/*
 * Copyright 2019 Mek Global Limited.
 */

package com.kumex.core.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @author chenshiwei
 * @email casocroz@gmail.com
 * @date 2019/10/15
 */
@Data
public class IndexItem {

    private String exchange;

    private BigDecimal price;

    private BigDecimal weight;

}
