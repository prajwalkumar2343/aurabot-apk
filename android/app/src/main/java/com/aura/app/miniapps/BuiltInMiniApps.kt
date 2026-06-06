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

    val fieldNotesReact = MiniAppBundle(
        id = "builtin.react_field_notes",
        runtime = "react",
        metadata = MiniAppMetadata(
            name = "Field Notes",
            description = "A local React smoke app for notes, navigation, and record editing.",
            category = "Productivity",
            builtIn = true
        ),
        theme = MiniAppTheme(primary = "#DC2626", secondary = "#0891B2", surface = "#101828"),
        icon = MiniAppIcon(value = "N", background = "#DC2626"),
        dataSchema = MiniAppDataSchema(
            recordType = "field_note",
            fields = listOf(
                MiniAppField("title", "text", required = true),
                MiniAppField("status", "text", required = true, defaultValue = "Open"),
                MiniAppField("note", "text")
            )
        ),
        actions = listOf(
            MiniAppAction("seed_note", "create_record", recordType = "field_note", values = mapOf("title" to "Runtime smoke", "status" to "Open", "note" to "Created by the built-in React app.")),
            MiniAppAction("show_notes", "query_records")
        ),
        assistantIntents = listOf(
            MiniAppAssistantIntent("open_field_notes", listOf("open field notes", "show field notes"), screenId = "dashboard"),
            MiniAppAssistantIntent("seed_field_note", listOf("add field note", "create field note"), actionId = "seed_note")
        ),
        capabilities = listOf("local_storage", "assistant_actions", "react_runtime", "scoped_storage"),
        codeBundle = MiniAppCodeBundle(
            entry = "App.jsx",
            appJsx = """
export default function App({ aura }) {
  const [view, setView] = React.useState("board");
  const [records, setRecords] = React.useState([]);
  const [editing, setEditing] = React.useState(null);
  // The shipped compiled bundle mirrors this source and calls aura.records CRUD.
}
""".trimIndent(),
            css = """
:root {
  color: #182230;
  font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}
* { box-sizing: border-box; }
body {
  background:
    radial-gradient(circle at top left, rgba(220, 38, 38, 0.16), transparent 34%),
    linear-gradient(145deg, #fff7ed 0%, #eef9fb 48%, #f8fafc 100%);
}
.app {
  min-height: 100vh;
  padding: 18px;
}
.shell {
  max-width: 760px;
  margin: 0 auto;
}
.hero {
  display: grid;
  gap: 14px;
  padding: 18px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.12);
  backdrop-filter: blur(14px);
}
.eyebrow {
  margin: 0;
  color: #0891b2;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0;
  text-transform: uppercase;
}
h1 {
  margin: 0;
  color: #111827;
  font-size: 30px;
  line-height: 1;
  letter-spacing: 0;
}
.subtitle {
  margin: 0;
  color: #475467;
  font-size: 14px;
  line-height: 1.45;
}
.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.stat, .card {
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.78);
}
.stat {
  padding: 12px;
}
.stat strong {
  display: block;
  color: #111827;
  font-size: 22px;
}
.stat span {
  color: #667085;
  font-size: 12px;
}
.tabs {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  margin: 14px 0;
}
button {
  min-height: 44px;
  border: 0;
  border-radius: 14px;
  background: #e5e7eb;
  color: #101828;
  font-weight: 800;
}
button.primary, .tabs button.active {
  background: #dc2626;
  color: white;
}
button.ghost {
  background: rgba(8, 145, 178, 0.12);
  color: #0e7490;
}
button.danger {
  background: rgba(220, 38, 38, 0.12);
  color: #b91c1c;
}
.grid {
  display: grid;
  gap: 12px;
}
.card {
  padding: 14px;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.08);
}
.note-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}
.note-title {
  margin: 0;
  color: #111827;
  font-size: 16px;
}
.pill {
  flex: 0 0 auto;
  border-radius: 999px;
  padding: 6px 9px;
  background: rgba(8, 145, 178, 0.14);
  color: #0e7490;
  font-size: 12px;
  font-weight: 800;
}
.note {
  margin: 8px 0 0;
  color: #475467;
  font-size: 14px;
  line-height: 1.45;
  white-space: pre-wrap;
}
.actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-top: 12px;
}
form {
  display: grid;
  gap: 10px;
}
label {
  display: grid;
  gap: 6px;
  color: #344054;
  font-size: 12px;
  font-weight: 800;
}
input, textarea, select {
  width: 100%;
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 14px;
  background: white;
  color: #111827;
  padding: 12px;
  outline: none;
}
textarea {
  min-height: 112px;
  resize: vertical;
}
.empty {
  padding: 22px;
  border: 1px dashed rgba(15, 23, 42, 0.18);
  border-radius: 18px;
  color: #667085;
  text-align: center;
}
.error {
  margin: 12px 0 0;
  color: #b42318;
  font-weight: 700;
}
@media (max-width: 420px) {
  .app { padding: 14px; }
  h1 { font-size: 26px; }
  .stats { grid-template-columns: 1fr; }
}
""".trimIndent(),
            compiledJs = """
(function() {
  function mount(root, aura) {
    var state = {
      view: "board",
      records: [],
      editingId: null,
      form: { title: "", status: "Open", note: "" },
      loading: true,
      error: ""
    };

    function escapeHtml(value) {
      return String(value || "").replace(/[&<>"']/g, function(ch) {
        return ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;" })[ch];
      });
    }

    function formatTime(value) {
      if (!value) return "Just now";
      return new Date(value).toLocaleString(undefined, { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });
    }

    function setState(patch) {
      Object.assign(state, patch);
      render();
    }

    function resetForm() {
      state.editingId = null;
      state.form = { title: "", status: "Open", note: "" };
    }

    function loadRecords() {
      setState({ loading: true, error: "" });
      aura.records.list("field_note").then(function(records) {
        setState({ records: records || [], loading: false });
      }).catch(function(error) {
        setState({ loading: false, error: error.message || "Could not load records" });
      });
    }

    function saveRecord() {
      var values = {
        title: state.form.title.trim(),
        status: state.form.status,
        note: state.form.note.trim()
      };
      if (!values.title) {
        setState({ error: "Title is required" });
        return;
      }
      setState({ error: "" });
      var request = state.editingId
        ? aura.records.update(state.editingId, values)
        : aura.records.create("field_note", values);
      request.then(function() {
        resetForm();
        state.view = "board";
        loadRecords();
      }).catch(function(error) {
        setState({ error: error.message || "Could not save note" });
      });
    }

    function editRecord(id) {
      var record = state.records.find(function(item) { return item.id === id; });
      if (!record) return;
      state.editingId = id;
      state.form = {
        title: record.values.title || "",
        status: record.values.status || "Open",
        note: record.values.note || ""
      };
      setState({ view: "edit", error: "" });
    }

    function deleteRecord(id) {
      aura.records.delete(id).then(loadRecords).catch(function(error) {
        setState({ error: error.message || "Could not delete note" });
      });
    }

    function seedRecord() {
      aura.records.create("field_note", {
        title: "Runtime smoke",
        status: "Open",
        note: "Created locally through the React bridge."
      }).then(loadRecords).catch(function(error) {
        setState({ error: error.message || "Could not add smoke note" });
      });
    }

    function renderBoard() {
      if (state.loading) return '<div class="empty">Loading local records...</div>';
      if (!state.records.length) {
        return '<div class="empty">No notes yet. Add one or seed a smoke note.</div>';
      }
      return '<div class="grid">' + state.records.map(function(record) {
        var values = record.values || {};
        return '<article class="card">' +
          '<div class="note-head"><div><h2 class="note-title">' + escapeHtml(values.title || "Untitled") + '</h2><p class="subtitle">' + escapeHtml(formatTime(record.updatedAt)) + '</p></div><span class="pill">' + escapeHtml(values.status || "Open") + '</span></div>' +
          '<p class="note">' + escapeHtml(values.note || "No note body.") + '</p>' +
          '<div class="actions"><button class="ghost" data-edit="' + escapeHtml(record.id) + '">Edit</button><button class="danger" data-delete="' + escapeHtml(record.id) + '">Delete</button></div>' +
        '</article>';
      }).join("") + '</div>';
    }

    function renderForm() {
      var verb = state.editingId ? "Update" : "Create";
      return '<article class="card"><form data-note-form="true">' +
        '<label>Title<input name="title" value="' + escapeHtml(state.form.title) + '" placeholder="Follow up with Sam"></label>' +
        '<label>Status<select name="status">' +
          ["Open", "Waiting", "Done"].map(function(status) {
            return '<option value="' + status + '"' + (state.form.status === status ? " selected" : "") + '>' + status + '</option>';
          }).join("") +
        '</select></label>' +
        '<label>Note<textarea name="note" placeholder="Capture the useful detail while it is fresh.">' + escapeHtml(state.form.note) + '</textarea></label>' +
        '<button class="primary" type="submit">' + verb + ' note</button>' +
      '</form></article>';
    }

    function render() {
      var openCount = state.records.filter(function(record) { return (record.values || {}).status !== "Done"; }).length;
      root.innerHTML = '<main class="app"><section class="shell">' +
        '<header class="hero"><p class="eyebrow">Built-in React Runtime</p><h1>Field Notes</h1><p class="subtitle">A bundled local app that navigates between views and exercises create, read, update, and delete records.</p>' +
        '<div class="stats"><div class="stat"><strong>' + state.records.length + '</strong><span>Total</span></div><div class="stat"><strong>' + openCount + '</strong><span>Open</span></div><div class="stat"><strong>' + (state.records.length - openCount) + '</strong><span>Done</span></div></div>' +
        '<button class="primary" data-seed="true">Seed smoke note</button></header>' +
        '<nav class="tabs"><button data-view="board" class="' + (state.view === "board" ? "active" : "") + '">Board</button><button data-view="edit" class="' + (state.view === "edit" ? "active" : "") + '">' + (state.editingId ? "Edit" : "New") + '</button></nav>' +
        (state.error ? '<p class="error">' + escapeHtml(state.error) + '</p>' : '') +
        (state.view === "edit" ? renderForm() : renderBoard()) +
      '</section></main>';
    }

    root.addEventListener("click", function(event) {
      var viewButton = event.target.closest("[data-view]");
      if (viewButton) {
        var view = viewButton.getAttribute("data-view");
        if (view === "edit" && state.view !== "edit") resetForm();
        setState({ view: view, error: "" });
        return;
      }
      if (event.target.closest("[data-seed]")) {
        seedRecord();
        return;
      }
      var editButton = event.target.closest("[data-edit]");
      if (editButton) {
        editRecord(editButton.getAttribute("data-edit"));
        return;
      }
      var deleteButton = event.target.closest("[data-delete]");
      if (deleteButton) {
        deleteRecord(deleteButton.getAttribute("data-delete"));
      }
    });

    root.addEventListener("submit", function(event) {
      if (!event.target.matches("[data-note-form]")) return;
      event.preventDefault();
      var form = new FormData(event.target);
      state.form = {
        title: form.get("title") || "",
        status: form.get("status") || "Open",
        note: form.get("note") || ""
      };
      saveRecord();
    });

    render();
    loadRecords();
  }

  window.__AuraMiniAppMount = mount;
})();
""".trimIndent(),
            allowedApis = listOf("records")
        )
    )

    val all = listOf(habitTracker, focusPlanner, spendTracker, fieldNotesReact)
}
