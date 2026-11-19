package com.cccece.picharbor

/** Product destinations are shared in meaning, while rendering stays native. */
enum class MobilePage(val route:String) {
    Photos("photos"), Backup("backup"), Activity("activity"), Settings("settings"),
    Preferences("preferences"), Checks("checks"), Pipeline("pipeline"), Scope("folders"),
    Network("network"), Proxy("proxy"), ProxyProfiles("proxy-profiles"), ProxyEditor("proxy-editor"), ProxyProfile("proxy-profile"),
    Archives("archives"), ArchiveSettings("archive-settings"), Interface("interface"),
    Accounts("accounts"), Connection("connection"), Permissions("permissions"), Background("background-settings"),
    Diagnostics("diagnostics"), About("about"), More("more"), Detail("detail"), Information("information"),
    TaskDetail("task-detail"), TaskProxy("task-proxy"), Batch("job-details"),
    Computer("live-link"), Cleanup("free-space"), BackupList("backup-list");
    val root get()=this in roots
    companion object {
        val roots get()=listOf(Photos,Backup,Activity,Settings)
        fun from(route:String)=entries.firstOrNull{it.route==route}
            ?:error("Unknown mobile destination: $route")
    }
}

data class MobileNavigationEntry(val page:MobilePage,val media:String="",val item:String="",val job:String="",val profile:String="",val status:String="all",val archived:Boolean=false,val key:String=java.util.UUID.randomUUID().toString())
