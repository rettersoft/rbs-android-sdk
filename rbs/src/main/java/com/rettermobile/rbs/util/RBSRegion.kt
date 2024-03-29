package com.rettermobile.rbs.util

/**
 * Created by semihozkoroglu on 15.03.2021.
 */
enum class RBSRegion(val getUrl: String, val postUrl: String) {
    EU_WEST_1(
        "https://core.rtbs.io/",
        "https://core-internal.rtbs.io/"
    ),
    EU_WEST_1_BETA(
        "https://core-test.rettermobile.com/",
        "https://core-internal-beta.rtbs.io/"
    )
}