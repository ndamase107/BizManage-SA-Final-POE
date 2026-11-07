package com.example.bizmanagezz.domain

import java.math.BigDecimal

object SaleRules {
    fun priceToCents(price: Double): Long {
        require(price.isFinite() && price >= 0) {
            "Price must be a valid non-negative amount."
        }

        return try {
            BigDecimal.valueOf(price)
                .movePointRight(2)
                .longValueExact()
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException(
                "Prices must have at most two decimal places and fit the supported range."
            )
        }
    }

    fun totalCents(
        unitPriceCents: Long,
        quantity: Int,
        availableStock: Int
    ): Long {
        require(unitPriceCents > 0) {
            "Selling price must be greater than zero."
        }
        require(quantity > 0) {
            "Quantity must be greater than zero."
        }
        require(availableStock >= quantity) {
            "Not enough stock for this sale."
        }

        return try {
            Math.multiplyExact(
                unitPriceCents,
                quantity.toLong()
            )
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException(
                "The sale total is too large."
            )
        }
    }
}
