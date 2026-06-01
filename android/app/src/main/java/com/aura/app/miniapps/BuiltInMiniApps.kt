package com.aura.app.miniapps

object BuiltInMiniApps {
    val habitTracker = MiniAppBundle(
        id = "builtin.habit_tracker",
        metadata = MiniAppMetadata(
            name = "Habit Tracker",
            description = "Track daily habits, streaks, history, and momentum.",
            category = "Wellness",
            builtIn = true
        ),
        theme = MiniAppTheme(primary = "#16A34A", secondary = "#06B6D4", surface = "#0B1220"),
        icon = MiniAppIcon(value = "H", background = "#16A34A"),
        dataSchema = MiniAppDataSchema(
            recordType = "habit_checkin",
            fields = listOf(
                MiniAppField("habit", "text", required = true),
                MiniAppField("done", "boolean", required = true, defaultValue = "true"),
                MiniAppField("note", "text")
            )
        ),
        actions = listOf(
            MiniAppAction("check_water", "create_record", values = mapOf("habit" to "Water", "done" to "true")),
            MiniAppAction("check_workout", "create_record", values = mapOf("habit" to "Workout", "done" to "true")),
            MiniAppAction("check_reading", "create_record", values = mapOf("habit" to "Reading", "done" to "true")),
            MiniAppAction("show_history", "query_records")
        ),
        assistantIntents = listOf(
            MiniAppAssistantIntent("mark_water_done", listOf("mark water done", "log water"), actionId = "check_water"),
            MiniAppAssistantIntent("mark_workout_done", listOf("mark workout done", "log workout"), actionId = "check_workout"),
            MiniAppAssistantIntent("show_my_streak", listOf("show my streak", "habit streak"), screenId = "dashboard")
        ),
        screens = listOf(
            MiniAppScreen(
                id = "dashboard",
                title = "Today",
                components = listOf(
                    MiniAppComponent("dashboard_block", "Daily Momentum", metric = "today_count"),
                    MiniAppComponent("streak_view", "Current Streak", metric = "streak"),
                    MiniAppComponent(
                        "quick_action_grid",
                        "Check in",
                        items = listOf(
                            MiniAppComponentItem("Water", "check_water"),
                            MiniAppComponentItem("Workout", "check_workout"),
                            MiniAppComponentItem("Reading", "check_reading")
                        )
                    ),
                    MiniAppComponent("timeline", "History", source = "records"),
                    MiniAppComponent("chart", "Last 7 Days", metric = "weekly_count")
                )
            )
        ),
        capabilities = listOf("local_storage", "assistant_actions")
    )

    val all = listOf(habitTracker)
}
