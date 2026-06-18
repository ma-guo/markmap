package com.zuxing.markmap

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

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

    @GET("/wxxcx/wechat/main/getAllStations")
    fun getAllStations(
        @Query("version") version: String = "1.34"
    ): Call<StationListResponse>

    @POST("/wxxcx/wechat/ticketinfo/getStopStation")
    @FormUrlEncoded
    fun getStopStation(
        @Field("train_no") trainNo: String,
        @Field("train_date") trainDate: String,
        @Field("f_station_telcode") fStationTelcode: String,
    ): Call<StopStationResponse>
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

data class StationListResponse(
    val status: Boolean = false,
    val data: StationListData? = null
)

data class StationListData(
    val stations: List<StationEntity>? = null
)

data class StationEntity(
    @SerializedName("station_name") val stationName: String? = null,
    @SerializedName("station_telecode") val stationTelecode: String? = null,
    @SerializedName("station_pycode") val stationPycode: String? = null,
    @SerializedName("station_firstcode") val stationFirstcode: String? = null,
    @SerializedName("province_name") val provinceName: String? = null,
    @SerializedName("city_name") val cityName: String? = null
)

data class StopStationResponse(
    val status: Boolean = false,
    val data: StopStationData? = null
)

data class StopStationData(
    val trainStopInfo: List<StopStationInfo>? = null
)

data class StopStationInfo(
    @SerializedName("station_name") val stationName: String? = null,
    @SerializedName("station_no") val stationNo: String? = null,
    @SerializedName("arrive_time") val arriveTime: String? = null,
    @SerializedName("start_time") val startTime: String? = null,
    @SerializedName("stopover_time") val stopoverTime: String? = null,
    @SerializedName("arrive_day_diff") val arriveDayDiff: String? = null,
    @SerializedName("start_day_diff") val startDayDiff: String? = null
)
