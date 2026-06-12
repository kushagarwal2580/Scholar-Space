fun main() {
    val formatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.US)
    try {
        val time = java.time.LocalTime.parse("09:00 AM", formatter)
        println("Success: $time")
    } catch(e: Exception) {
        println("Error: ${e.message}")
    }
}
