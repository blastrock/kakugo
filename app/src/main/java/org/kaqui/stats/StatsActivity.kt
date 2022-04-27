package org.kaqui.stats

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import org.jetbrains.anko.frameLayout
import org.kaqui.BaseActivity

class StatsActivity : BaseActivity() {
    companion object {
        const val TAG = "StatsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var frameLayoutId = 0
        frameLayout {
            id = View.generateViewId()
            frameLayoutId = id
        }
        supportFragmentManager.commit {
            replace(frameLayoutId, TestStatsFragment())
        }
    }
}
