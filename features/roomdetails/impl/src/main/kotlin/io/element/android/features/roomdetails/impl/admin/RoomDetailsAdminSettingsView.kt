/*
 * Copyright (c) 2024 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.element.android.features.roomdetails.impl.admin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListSectionHeader
import io.element.android.libraries.designsystem.theme.components.Text

@Composable
fun RoomDetailsAdminSettingsView(
    state: RoomDetailsAdminSettingsState,
    roomDetailsAdminSettingsNavigator: RoomDetailsAdminSettingsNavigator,
    modifier: Modifier = Modifier,
) {
    PreferencePage(
        modifier = modifier,
        title = "Role and permissions",
        onBackPressed = roomDetailsAdminSettingsNavigator::onBackPressed,
    ) {
        ListSectionHeader(title = "Roles", hasDivider = false)
        ListItem(
            headlineContent = { Text("Admins") },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Admin())),
            trailingContent = ListItemContent.Text("${state.adminCount}"),
            onClick = { roomDetailsAdminSettingsNavigator.openAdminList() },
        )
        ListItem(
            headlineContent = { Text("Moderators") },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.ChatProblem())),
            trailingContent = ListItemContent.Text("${state.moderatorCount}"),
            onClick = { roomDetailsAdminSettingsNavigator.openModeratorList() },
        )
    }
}

@Preview
@Composable
internal fun RoomDetailsAdminSettingsViewPreview() {
    ElementPreview {
        RoomDetailsAdminSettingsView(
            state = RoomDetailsAdminSettingsState(
                adminCount = 1,
                moderatorCount = 2,
            ),
            roomDetailsAdminSettingsNavigator = object : RoomDetailsAdminSettingsNavigator {},
        )
    }
}
