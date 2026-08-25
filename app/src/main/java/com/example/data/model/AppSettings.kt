package com.example.data.model

data class AppSettings(
    val wsServerUrl: String = "wss://echo.websocket.events",
    val wsAuthToken: String = "",
    val autoReconnect: Boolean = true,
    val showroomName: String = "Downtown Luxury Showroom",
    val showroomAddress: String = "100 Grand Avenue, Financial District, New York, NY 10001",
    val showroomMapsUrl: String = "https://maps.google.com/?q=40.712776,-74.005974(Downtown+Luxury+Showroom)",
    val overlayEnabled: Boolean = true,
    val autoOpenOverlayOnCallEnd: Boolean = true,
    val customTags: List<String> = listOf(
        "🔥 Hot Lead",
        "📍 Showroom Visit",
        "🤝 Deal Closed",
        "⏳ Follow Up",
        "💬 Info Requested",
        "⭐ VIP Client",
        "❌ Not Interested",
        "🚫 Wrong Number"
    )
)
