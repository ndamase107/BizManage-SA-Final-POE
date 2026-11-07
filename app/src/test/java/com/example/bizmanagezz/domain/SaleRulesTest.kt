package com.example.bizmanagezz.domain
import org.junit.Assert.assertEquals
import org.junit.Test


class SaleRulesTest {

    @Test
    fun convertsRandToExactCents() {
        assertEquals(
            1250L,
            SaleRules.priceToCents(12.50)
        )
    }

    @Test
    fun calculatesThreeItems() {
        assertEquals(
            3750L,
            SaleRules.totalCents(
                unitPriceCents = 1250,
                quantity = 3,
                availableStock = 10
            )
        )
    }

    @Test
    fun allowsSellingLastItem() {
        assertEquals(
            1250L,
            SaleRules.totalCents(
                unitPriceCents = 1250,
                quantity = 1,
                availableStock = 1
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOverselling() {
        SaleRules.totalCents(1250, 3, 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroQuantity() {
        SaleRules.totalCents(1250, 0, 10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeQuantity() {
        SaleRules.totalCents(1250, -1, 10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroSellingPrice() {
        SaleRules.totalCents(0, 1, 10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidPrice() {
        SaleRules.priceToCents(Double.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFractionsOfACent() {
        SaleRules.priceToCents(12.345)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTotalOverflow() {
        SaleRules.totalCents(Long.MAX_VALUE, 2, 10)
    }
}