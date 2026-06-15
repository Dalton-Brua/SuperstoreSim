package com.example.superstoresimulator.ui.screens.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.research.AnalystAssignment
import com.example.superstoresimulator.domain.research.ProductDepartment
import com.example.superstoresimulator.domain.research.ResearchCategory
import com.example.superstoresimulator.domain.research.ResearchUpgradeRegistry
import com.example.superstoresimulator.domain.research.ResearchableUpgrade
import com.example.superstoresimulator.ui.state.ResearchUIState
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.LightBackground
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.TextMuted
import com.example.superstoresimulator.ui.theme.TextSecondary

@Composable
fun UnlocksScreen(
    research: ResearchUIState,
    money: Money,
    modifier: Modifier = Modifier,
    onAssignAnalyst: (entityId: Int, assignment: AnalystAssignment?) -> Unit = { _, _ -> },
) {
    // Group visible upgrades by category, ordered by the enum declaration.
    val grouped = research.visibleUpgrades.groupBy { it.category }
    val categories = ResearchCategory.entries.filter { grouped[it]?.isNotEmpty() == true }

    var selectedCategory by remember { mutableStateOf<ResearchCategory?>(null) }
    var selectedDepartment by remember { mutableStateOf<ProductDepartment?>(null) }

    // Drop selection if the category no longer has visible research.
    val activeCategory = selectedCategory?.takeIf { it in categories }

    when {
        activeCategory == null -> {
            CategoryListScreen(
                categories = categories,
                grouped = grouped,
                research = research,
                onSelect = {
                    selectedCategory = it
                    selectedDepartment = null
                },
                modifier = modifier,
            )
        }

        // Product Lines drills one level deeper into store departments.
        activeCategory == ResearchCategory.PRODUCT_LINES && selectedDepartment == null -> {
            DepartmentListScreen(
                research = research,
                visibleProduct = grouped[ResearchCategory.PRODUCT_LINES].orEmpty(),
                onSelect = { selectedDepartment = it },
                onBack = { selectedCategory = null },
                modifier = modifier,
            )
        }

        activeCategory == ResearchCategory.PRODUCT_LINES -> {
            val dept = selectedDepartment!!
            val visible = grouped[ResearchCategory.PRODUCT_LINES].orEmpty()
                .filter { ResearchUpgradeRegistry.departmentOf(it.id) == dept }
            ResearchDetailScreen(
                title = dept.displayName,
                upgrades = visible,
                lockedNext = lockedNextFor(departmentPool(dept), research, visible),
                research = research,
                onAssignAnalyst = onAssignAnalyst,
                onBack = { selectedDepartment = null },
                modifier = modifier,
            )
        }

        else -> {
            val visible = grouped[activeCategory].orEmpty()
            val pool = ResearchUpgradeRegistry.allUpgrades.values
                .filter { it.category == activeCategory }
            ResearchDetailScreen(
                title = activeCategory.displayName,
                upgrades = visible,
                lockedNext = lockedNextFor(pool, research, visible),
                research = research,
                onAssignAnalyst = onAssignAnalyst,
                onBack = { selectedCategory = null },
                modifier = modifier,
            )
        }
    }
}

private fun departmentPool(dept: ProductDepartment): List<ResearchableUpgrade> =
    ResearchUpgradeRegistry.allUpgrades.values
        .filter { ResearchUpgradeRegistry.departmentOf(it.id) == dept }

/**
 * Next-step research within [pool]: not researched, not yet available, but every prerequisite
 * is already met or currently available — i.e. it unlocks after the next upgrade.
 */
private fun lockedNextFor(
    pool: Collection<ResearchableUpgrade>,
    research: ResearchUIState,
    visible: List<ResearchableUpgrade>,
): List<ResearchableUpgrade> {
    val visibleIds = visible.map { it.id }.toSet()
    return pool.filter { up ->
        up.id !in research.researchedUpgrades &&
            up.id !in visibleIds &&
            up.prerequisites.all { it in research.researchedUpgrades || it in visibleIds }
    }
}

@Composable
private fun CategoryListScreen(
    categories: List<ResearchCategory>,
    grouped: Map<ResearchCategory, List<ResearchableUpgrade>>,
    research: ResearchUIState,
    onSelect: (ResearchCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Research",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryDark,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Points earned: ${research.totalPointsEarned.toInt()}",
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        items(categories) { category ->
            val upgrades = grouped[category].orEmpty()
            val researched = upgrades.count { it.id in research.researchedUpgrades }
            val categoryIds = ResearchUpgradeRegistry.allUpgrades.values
                .filter { it.category == category }.map { it.id }.toSet()
            DrillCard(
                title = category.displayName,
                subtitle = "$researched / ${upgrades.size} researched",
                researchingCount = researchingCountFor(research, categoryIds),
                onClick = { onSelect(category) },
            )
        }
    }
}

@Composable
private fun DepartmentListScreen(
    research: ResearchUIState,
    visibleProduct: List<ResearchableUpgrade>,
    onSelect: (ProductDepartment) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleByDept = visibleProduct.groupBy { ResearchUpgradeRegistry.departmentOf(it.id) }
    // Show a department if it has available research or an upcoming (locked) next step.
    val departments = ProductDepartment.entries.filter { dept ->
        val visible = visibleByDept[dept].orEmpty()
        visible.isNotEmpty() || lockedNextFor(departmentPool(dept), research, visible).isNotEmpty()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            BackHeader(title = ResearchCategory.PRODUCT_LINES.displayName, onBack = onBack)
        }

        items(departments) { dept ->
            val visible = visibleByDept[dept].orEmpty()
            val researched = visible.count { it.id in research.researchedUpgrades }
            val deptIds = departmentPool(dept).map { it.id }.toSet()
            DrillCard(
                title = dept.displayName,
                subtitle = if (visible.isEmpty()) "🔒 Locked"
                else "$researched / ${visible.size} researched",
                researchingCount = researchingCountFor(research, deptIds),
                onClick = { onSelect(dept) },
            )
        }
    }
}

@Composable
private fun DrillCard(
    title: String,
    subtitle: String,
    researchingCount: Int,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(CardWhite),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDark,
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
                if (researchingCount > 0) {
                    Text(
                        text = "$researchingCount researching",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Primary,
                    )
                }
            }
            Text(text = "›", fontSize = 22.sp, color = TextMuted)
        }
    }
}

/** Number of analysts currently researching an upgrade within [upgradeIds]. */
private fun researchingCountFor(research: ResearchUIState, upgradeIds: Set<String>): Int =
    research.analysts.count { analyst ->
        val assignment = analyst.assignment
        assignment is AnalystAssignment.Research && assignment.upgradeId in upgradeIds
    }

@Composable
private fun ResearchDetailScreen(
    title: String,
    upgrades: List<ResearchableUpgrade>,
    lockedNext: List<ResearchableUpgrade>,
    research: ResearchUIState,
    onAssignAnalyst: (entityId: Int, assignment: AnalystAssignment?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalAnalysts = research.analysts.size
    val idleAnalysts = research.analysts.count { it.assignment == null }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            BackHeader(title = title, onBack = onBack)
            Text(
                text = if (totalAnalysts == 0) {
                    "Hire a Market Analyst (Staff tab) to begin researching."
                } else {
                    "Analysts: $idleAnalysts idle of $totalAnalysts"
                },
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        items(upgrades) { upgrade ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(CardWhite),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    UpgradeRow(
                        upgrade = upgrade,
                        research = research,
                        onAssignAnalyst = onAssignAnalyst,
                    )
                }
            }
        }

        items(lockedNext) { upgrade ->
            LockedTeaserCard(upgrade = upgrade, research = research)
        }
    }
}

@Composable
private fun BackHeader(title: String, onBack: () -> Unit) {
    Text(
        text = "‹ Back",
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = PrimaryDark,
        modifier = Modifier
            .clickable(onClick = onBack)
            .padding(vertical = 4.dp, horizontal = 2.dp)
    )
    Text(
        text = title,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = PrimaryDark,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun LockedTeaserCard(
    upgrade: ResearchableUpgrade,
    research: ResearchUIState,
) {
    // Name the prerequisite(s) still needed; reveal the prereq name, not this research.
    val missingPrereqs = upgrade.prerequisites
        .filter { it !in research.researchedUpgrades }
        .mapNotNull { ResearchUpgradeRegistry.allUpgrades[it]?.displayName }
    val requirement = if (missingPrereqs.isEmpty()) {
        "Complete the research above to reveal this."
    } else {
        "Requires: ${missingPrereqs.joinToString(", ")}"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(CardWhite),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "🔒", fontSize = 18.sp)
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(
                    text = "Locked",
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted,
                )
                Text(
                    text = requirement,
                    fontSize = 12.sp,
                    color = TextMuted,
                )
            }
        }
    }
}

@Composable
private fun UpgradeRow(
    upgrade: ResearchableUpgrade,
    research: ResearchUIState,
    onAssignAnalyst: (entityId: Int, assignment: AnalystAssignment?) -> Unit,
) {
    val isComplete = upgrade.id in research.researchedUpgrades
    val progress = research.researchProgress[upgrade.id] ?: 0f
    val fraction = (progress / upgrade.researchCost).coerceIn(0f, 1f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(upgrade.displayName, fontWeight = FontWeight.SemiBold, color = PrimaryDark)
        Text(upgrade.description, fontSize = 12.sp, color = TextMuted)
        if (!isComplete) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
            Text(
                text = "${progress.toInt()} / ${upgrade.researchCost.toInt()} pts",
                fontSize = 11.sp,
                color = TextSecondary,
            )
            AnalystAssignmentRow(
                upgrade = upgrade,
                research = research,
                onAssignAnalyst = onAssignAnalyst,
            )
        } else {
            Text("Researched", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color(0xFF2E7D32))
        }
    }
}

@Composable
private fun AnalystAssignmentRow(
    upgrade: ResearchableUpgrade,
    research: ResearchUIState,
    onAssignAnalyst: (entityId: Int, assignment: AnalystAssignment?) -> Unit,
) {
    val assignedHere = research.analysts.filter {
        it.assignment == AnalystAssignment.Research(upgrade.id)
    }
    val idleAnalyst = research.analysts.firstOrNull { it.assignment == null }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = when {
                assignedHere.isNotEmpty() -> "${assignedHere.size} researching"
                else -> "No analyst assigned"
            },
            fontSize = 11.sp,
            color = if (assignedHere.isNotEmpty()) Primary else TextMuted,
            modifier = Modifier.weight(1f),
        )
        if (assignedHere.isNotEmpty()) {
            TextButton(onClick = { onAssignAnalyst(assignedHere.first().id, null) }) {
                Text("Unassign", fontSize = 12.sp, color = TextSecondary)
            }
        }
        Button(
            onClick = {
                idleAnalyst?.let { onAssignAnalyst(it.id, AnalystAssignment.Research(upgrade.id)) }
            },
            enabled = idleAnalyst != null,
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
        ) {
            Text("Assign", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.White)
        }
    }
}
