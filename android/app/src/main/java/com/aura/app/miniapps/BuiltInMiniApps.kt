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
                    MiniAppComponent("chart", "Last 7 Days", metric = "weekly_count"),
                    MiniAppComponent("slider", "Weekly Goal", metric = "weekly_count")
                )
            ),
            MiniAppScreen(
                id = "coach",
                title = "Coach",
                components = listOf(
                    MiniAppComponent(
                        "form",
                        "Custom Check-in",
                        items = listOf(MiniAppComponentItem("Save check-in"))
                    ),
                    MiniAppComponent(
                        "list",
                        "Routine",
                        items = listOf(
                            MiniAppComponentItem("Hydration", "check_water", "Tiny daily baseline"),
                            MiniAppComponentItem("Movement", "check_workout", "Protect your energy"),
                            MiniAppComponentItem("Reading", "check_reading", "Keep the mind warm")
                        )
                    ),
                    MiniAppComponent(
                        "bottom_sheet",
                        "Momentum note",
                        items = listOf(MiniAppComponentItem("Streaks grow best when check-ins stay lightweight."))
                    ),
                    MiniAppComponent("button", "Log water now", actionId = "check_water"),
                    MiniAppComponent("settings", "App setup")
                )
            )
        ),
        capabilities = listOf("local_storage", "assistant_actions")
    )

    val focusPlanner = MiniAppBundle(
        id = "builtin.focus_planner",
        metadata = MiniAppMetadata(
            name = "Focus Planner",
            description = "Plan deep-work blocks, log wins, and review your focus rhythm.",
            category = "Productivity",
            builtIn = true
        ),
        theme = MiniAppTheme(primary = "#2563EB", secondary = "#F59E0B", surface = "#111827"),
        icon = MiniAppIcon(value = "F", background = "#2563EB"),
        dataSchema = MiniAppDataSchema(
            recordType = "focus_session",
            fields = listOf(
                MiniAppField("session", "text", required = true),
                MiniAppField("minutes", "number", required = true),
                MiniAppField("note", "text")
            )
        ),
        actions = listOf(
            MiniAppAction("plan_deep_work", "create_record", recordType = "focus_session", values = mapOf("session" to "Deep work", "minutes" to "60")),
            MiniAppAction("plan_admin", "create_record", recordType = "focus_session", values = mapOf("session" to "Admin sweep", "minutes" to "25")),
            MiniAppAction("log_win", "create_record", recordType = "focus_session", values = mapOf("session" to "Win logged", "minutes" to "10")),
            MiniAppAction("show_focus_history", "query_records")
        ),
        assistantIntents = listOf(
            MiniAppAssistantIntent("plan_deep_work", listOf("plan deep work", "start a focus block"), actionId = "plan_deep_work"),
            MiniAppAssistantIntent("log_focus_win", listOf("log a focus win", "save my focus win"), actionId = "log_win"),
            MiniAppAssistantIntent("show_focus_history", listOf("show focus history", "focus planner history"), screenId = "dashboard")
        ),
        screens = listOf(
            MiniAppScreen(
                id = "dashboard",
                title = "Focus Board",
                components = listOf(
                    MiniAppComponent("dashboard_block", "Sessions Today", metric = "today_count"),
                    MiniAppComponent("streak_view", "Focus Streak", metric = "streak"),
                    MiniAppComponent(
                        "quick_action_grid",
                        "Plan",
                        items = listOf(
                            MiniAppComponentItem("Deep Work", "plan_deep_work"),
                            MiniAppComponentItem("Admin", "plan_admin"),
                            MiniAppComponentItem("Win", "log_win")
                        )
                    ),
                    MiniAppComponent("chart", "Focus Rhythm", metric = "weekly_count"),
                    MiniAppComponent("timeline", "Session Log", source = "records"),
                    MiniAppComponent("slider", "Weekly Focus Pace", metric = "weekly_count")
                )
            ),
            MiniAppScreen(
                id = "plan",
                title = "Plan",
                components = listOf(
                    MiniAppComponent(
                        "form",
                        "Custom Session",
                        items = listOf(MiniAppComponentItem("Save session"))
                    ),
                    MiniAppComponent(
                        "list",
                        "Focus Menu",
                        items = listOf(
                            MiniAppComponentItem("Deep Work", "plan_deep_work", "60 minute protected block"),
                            MiniAppComponentItem("Admin Sweep", "plan_admin", "25 minute cleanup sprint"),
                            MiniAppComponentItem("Win Log", "log_win", "Capture a completed outcome")
                        )
                    ),
                    MiniAppComponent("button", "Start deep work", actionId = "plan_deep_work"),
                    MiniAppComponent(
                        "bottom_sheet",
                        "Planning note",
                        items = listOf(MiniAppComponentItem("Use Focus Planner when the day needs one next obvious block, not a giant plan."))
                    ),
                    MiniAppComponent("settings", "Planner setup")
                )
            )
        ),
        capabilities = listOf("local_storage", "assistant_actions")
    )

    val spendTracker = MiniAppBundle(
        id = "builtin.spend_tracker",
        metadata = MiniAppMetadata(
            name = "Spend Tracker",
            description = "Capture everyday spending, spot weekly patterns, and keep a local ledger.",
            category = "Finance",
            builtIn = true
        ),
        theme = MiniAppTheme(primary = "#0F766E", secondary = "#A855F7", surface = "#111827"),
        icon = MiniAppIcon(value = "S", background = "#0F766E"),
        dataSchema = MiniAppDataSchema(
            recordType = "expense",
            fields = listOf(
                MiniAppField("merchant", "text", required = true),
                MiniAppField("amount", "number", required = true),
                MiniAppField("category", "text", required = true)
            )
        ),
        actions = listOf(
            MiniAppAction("coffee", "create_record", recordType = "expense", values = mapOf("merchant" to "Coffee", "amount" to "5", "category" to "Food")),
            MiniAppAction("transport", "create_record", recordType = "expense", values = mapOf("merchant" to "Transport", "amount" to "12", "category" to "Travel")),
            MiniAppAction("groceries", "create_record", recordType = "expense", values = mapOf("merchant" to "Groceries", "amount" to "35", "category" to "Home")),
            MiniAppAction("show_spend", "query_records")
        ),
        assistantIntents = listOf(
            MiniAppAssistantIntent("log_coffee", listOf("log coffee spend", "add coffee expense"), actionId = "coffee"),
            MiniAppAssistantIntent("log_transport", listOf("log transport spend", "add transport expense"), actionId = "transport"),
            MiniAppAssistantIntent("show_spending", listOf("show my spending", "spend tracker history"), screenId = "dashboard")
        ),
        screens = listOf(
            MiniAppScreen(
                id = "dashboard",
                title = "Ledger",
                components = listOf(
                    MiniAppComponent("dashboard_block", "Entries Today", metric = "today_count"),
                    MiniAppComponent("chart", "Weekly Spend Log", metric = "weekly_count"),
                    MiniAppComponent(
                        "quick_action_grid",
                        "Quick Log",
                        items = listOf(
                            MiniAppComponentItem("Coffee", "coffee"),
                            MiniAppComponentItem("Transport", "transport"),
                            MiniAppComponentItem("Groceries", "groceries")
                        )
                    ),
                    MiniAppComponent("timeline", "Recent Expenses", source = "records"),
                    MiniAppComponent("slider", "Weekly Entry Pace", metric = "weekly_count")
                )
            ),
            MiniAppScreen(
                id = "categories",
                title = "Categories",
                components = listOf(
                    MiniAppComponent(
                        "form",
                        "New Expense",
                        items = listOf(MiniAppComponentItem("Save expense"))
                    ),
                    MiniAppComponent(
                        "list",
                        "Quick Categories",
                        items = listOf(
                            MiniAppComponentItem("Food", "coffee", "Coffee, lunch, snacks"),
                            MiniAppComponentItem("Travel", "transport", "Transit and rides"),
                            MiniAppComponentItem("Home", "groceries", "Groceries and basics")
                        )
                    ),
                    MiniAppComponent("button", "Log groceries", actionId = "groceries"),
                    MiniAppComponent(
                        "bottom_sheet",
                        "Ledger note",
                        items = listOf(MiniAppComponentItem("This is a quick local ledger, built for capture first and analysis later."))
                    ),
                    MiniAppComponent("settings", "Ledger setup")
                )
            )
        ),
        capabilities = listOf("local_storage", "assistant_actions")
    )

    val all = listOf(habitTracker, focusPlanner, spendTracker)
}
