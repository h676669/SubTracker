package com.subtracker.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.subtracker.data.AppDatabase
import com.subtracker.data.Subscription
import com.subtracker.data.isActive
import com.subtracker.data.monthlyCost
import com.subtracker.data.nextCharge
import com.subtracker.ui.MainActivity
import com.subtracker.ui.daysLabel
import com.subtracker.ui.kr
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import androidx.glance.material3.ColorProviders
import com.subtracker.ui.Appearance
import com.subtracker.ui.ThemeState
import com.subtracker.ui.schemeFor

class SubWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val active = AppDatabase.get(context).dao().getAll().filter { it.isActive }
        val upcoming = active
            .mapNotNull { s -> s.nextCharge(today)?.let { s to it } }
            .sortedBy { it.second }
            .take(4)
        val monthly = active.sumOf { it.monthlyCost }
        val openApp = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        // Match the theme chosen in the app (read straight from prefs).
        val paletteId = ThemeState.paletteIdOf(context)
        val appearance = ThemeState.appearanceOf(context)
        val light = schemeFor(context, paletteId, dark = false)
        val dark = schemeFor(context, paletteId, dark = true)
        val colors = when (appearance) {
            Appearance.LIGHT -> ColorProviders(light = light, dark = light)
            Appearance.DARK -> ColorProviders(light = dark, dark = dark)
            Appearance.SYSTEM -> ColorProviders(light = light, dark = dark)
        }

        provideContent {
            GlanceTheme(colors = colors) { WidgetContent(monthly, upcoming, today, openApp) }
        }
    }
}

class SubWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SubWidget()
}

// Glance rows/columns allow at most 10 children, so rows use padding instead of spacers.
@Composable
private fun WidgetContent(
    monthly: Double,
    upcoming: List<Pair<Subscription, LocalDate>>,
    today: LocalDate,
    openApp: Intent,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(14.dp)
            .clickable(actionStartActivity(openApp)),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Subscriptions",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                "${kr(monthly)}/mo",
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(GlanceModifier.height(6.dp))

        if (upcoming.isEmpty()) {
            Text(
                "No upcoming charges",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            )
        }
        upcoming.forEach { (sub, date) ->
            val soon = ChronoUnit.DAYS.between(today, date) <= 3
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = GlanceModifier.size(8.dp).cornerRadius(4.dp).background(Color(sub.color)),
                ) {}
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    sub.name,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(
                    "${kr(sub.price)} · ${daysLabel(date, today)}",
                    style = TextStyle(
                        color = if (soon) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = if (soon) FontWeight.Bold else FontWeight.Normal,
                    ),
                )
            }
        }
    }
}
