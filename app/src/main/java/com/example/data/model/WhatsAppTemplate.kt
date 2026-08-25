package com.example.data.model

data class WhatsAppTemplate(
    val id: String,
    val title: String,
    val description: String,
    val iconType: String, // "LOCATION", "CATALOG", "APPOINTMENT", "THANKS", "CUSTOM"
    val templateText: String
) {
    fun formatMessage(
        phoneNumber: String,
        showroomName: String,
        showroomAddress: String,
        showroomMapsUrl: String
    ): String {
        return templateText
            .replace("{number}", phoneNumber)
            .replace("{showroom_name}", showroomName)
            .replace("{address}", showroomAddress)
            .replace("{maps_url}", showroomMapsUrl)
    }

    companion object {
        fun getDefaultTemplates(): List<WhatsAppTemplate> {
            return listOf(
                WhatsAppTemplate(
                    id = "tpl_location",
                    title = "Showroom Google Maps Location",
                    description = "Send interactive Google Maps URL with address and directions",
                    iconType = "LOCATION",
                    templateText = "Hello! 👋 Thank you for contacting us.\n\n📍 Here is our official showroom location:\n{maps_url}\n\n🏢 Address:\n{address}\n\nWe look forward to welcoming you at *{showroom_name}*!"
                ),
                WhatsAppTemplate(
                    id = "tpl_catalog",
                    title = "Product Catalog & Brochure",
                    description = "Share our digital brochure and price list link",
                    iconType = "CATALOG",
                    templateText = "Hello! 📄 As requested, here is our latest product catalog and collection highlights:\nhttps://example.com/showroom-catalog\n\nFeel free to let us know if you would like to reserve an item or have any questions!"
                ),
                WhatsAppTemplate(
                    id = "tpl_appointment",
                    title = "Book Showroom VIP Visit",
                    description = "Confirm showroom visit slot & VIP walkthrough",
                    iconType = "APPOINTMENT",
                    templateText = "Hi! 🗓️ We would love to arrange a personalized VIP walkthrough for you at *{showroom_name}*.\n\n📍 Location: {maps_url}\n⏰ Please reply with your preferred day and time!"
                ),
                WhatsAppTemplate(
                    id = "tpl_thanks",
                    title = "Quick Follow-Up / Thank You",
                    description = "Friendly thank-you note with representative contact",
                    iconType = "THANKS",
                    templateText = "Hi! Thank you for taking our call today. It was a pleasure speaking with you. Don't hesitate to reach out here anytime if you need further assistance! 🤝"
                )
            )
        }
    }
}
