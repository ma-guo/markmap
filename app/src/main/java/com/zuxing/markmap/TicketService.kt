package com.zuxing.markmap

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface TicketService {

    @GET("/otn/leftTicket/queryB")
    fun queryTickets(
        @Query("leftTicketDTO.train_date") trainDate: String,
        @Query("leftTicketDTO.from_station") fromStation: String,
        @Query("leftTicketDTO.to_station") toStation: String,
        @Query("purpose_codes") purposeCodes: String = "ADULT",
    ): Call<TicketQueryResponse>
}

data class TicketQueryResponse(
    val httpstatus: Int = 0,
    val status: Boolean = false,
    val messages: String? = null,
    val data: TicketQueryData? = null
)

data class TicketQueryData(
    val result: List<String>? = null,
    val flag: String? = null,
    val level: String? = null,
    val map: Map<String, String>? = null
)

data class TicketInfo(
    val trainNo: String,
    val stationTrainCode: String,
    val fromStation: String,
    val toStation: String,
    val fromTelecode: String,
    val toTelecode: String,
    val startTime: String,
    val arriveTime: String,
    val duration: String,
    val canBuy: Boolean,
    val seats: List<String>,
    val prices: Map<String, Int>,
    val date: String
)