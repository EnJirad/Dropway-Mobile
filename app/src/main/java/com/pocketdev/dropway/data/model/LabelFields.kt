package com.pocketdev.dropway.data.model

data class LabelFields(
    val name: String = "",
    val phone: String = "",
    val houseNo: String = "",
    val address: String = "",
    val postalCode: String = "",
    val tracking: String = ""
) {
    fun hasAnyField(): Boolean =
        name.isNotBlank() || phone.isNotBlank() || houseNo.isNotBlank() ||
        address.isNotBlank() || postalCode.isNotBlank() || tracking.isNotBlank()
}
