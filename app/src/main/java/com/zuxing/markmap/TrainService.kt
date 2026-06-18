package com.zuxing.markmap

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface TrainService {

    @POST("/wxxcx/wechat/main/travelServiceQrcodeTrainInfo")
    @FormUrlEncoded
    fun getTrainInfo(
        @Field("trainCode") trainCode: String,
        @Field("startDay") startDay: String,
        @Field("startTime") startTime: String = "",
        @Field("endDay") endDay: String = "",
        @Field("endTime") endTime: String = "",
    ): Call<TrainInfoResponse>

    @POST("/wxxcx/wechat/main/getTrainMapLine")
    @FormUrlEncoded
    fun getTrainMapLine(
        @Field("version") version: String = "v2",
        @Field("trainNo") trainNo: String,
    ): Call<TrainMapResponse>
}

data class TrainInfoResponse(
    val httpStatus: Int = 0,
    val message: String? = null,
    val data: TrainInfoData? = null
)

data class TrainInfoData(
    val trainNo: String? = null,
    val trainCode: String? = null,
    val trainDetail: TrainDetail? = null
)

data class TrainDetail(
    val stopTime: List<TrainStopTime>? = null
)

data class TrainStopTime(
    val stationName: String? = null,
    val stationNo: String? = null,
    val lon: String? = null,
    val lat: String? = null,
    val arriveTime: String? = null,
    val startTime: String? = null,
    val runningTime: String? = null,
    @SerializedName("stopover_time") val stopoverTime: String? = null,
    val dayDifference: String? = null
)

data class TrainMapResponse(
    val status: Boolean = false,
    val errorCode: String? = null,
    val errorMsg: String? = null,
    val data: Map<String, TrainLineSegment>? = null
)

data class TrainLineSegment(
    val line: List<List<Double>>? = null,
    val index: Int = 0
)
