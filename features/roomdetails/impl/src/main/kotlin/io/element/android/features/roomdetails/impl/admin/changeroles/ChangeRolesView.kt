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

package io.element.android.features.roomdetails.impl.admin.changeroles

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.roomdetails.impl.members.aRoomMember
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.designsystem.components.ProgressDialog
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.aliasScreenTitle
import io.element.android.libraries.designsystem.theme.components.Checkbox
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.SearchBar
import io.element.android.libraries.designsystem.theme.components.SearchBarResultState
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.components.MatrixUserRow
import io.element.android.libraries.matrix.ui.components.SelectedUsersRowList
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeRolesView(
    state: ChangeRolesState,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val updatedOnBackPressed by rememberUpdatedState(newValue = onBackPressed)
    BackHandler {
        if (state.isSearchActive) {
            state.eventSink(ChangeRolesEvent.ToggleSearchActive)
        } else {
            state.eventSink(ChangeRolesEvent.Exit)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding(),
        topBar = {
            AnimatedVisibility(visible = !state.isSearchActive) {
                TopAppBar(
                    title = {
                        val title = when (state.role) {
                            RoomMember.Role.ADMIN -> "Edit admins"
                            RoomMember.Role.MODERATOR -> "Edit moderators"
                            RoomMember.Role.USER -> error("This can never be reached")
                        }
                        Text(
                            text = title,
                            style = ElementTheme.typography.aliasScreenTitle,
                        )
                    },
                    navigationIcon = {
                        BackButton(onClick = { state.eventSink(ChangeRolesEvent.Exit) })
                    },
                    actions = {
                        TextButton(
                            text = stringResource(CommonStrings.action_save),
                            enabled = state.hasPendingChanges,
                            onClick = { state.eventSink(ChangeRolesEvent.Save) }
                        )
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues),
        ) {
            val lazyListState = rememberLazyListState()
            SearchBar(
                placeHolderTitle = stringResource(CommonStrings.common_search_for_someone),
                query = state.query.orEmpty(),
                onQueryChange = { state.eventSink(ChangeRolesEvent.QueryChanged(it)) },
                active = state.isSearchActive,
                onActiveChange = { state.eventSink(ChangeRolesEvent.ToggleSearchActive) },
                resultState = state.searchResults,
            ) {
                SearchResultsList(
                    lazyListState = lazyListState,
                    searchResults = it,
                    selectedUsers = state.selectedUsers,
                    canRemoveMember = state.canRemoveMember,
                    onSelectionToggled = { state.eventSink(ChangeRolesEvent.UserSelectionToggled(it)) },
                )
            }
            AnimatedVisibility(
                visible = !state.isSearchActive,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column {
                    SelectedUsersRowList(
                        contentPadding = PaddingValues(16.dp),
                        selectedUsers = state.selectedUsers,
                        onUserRemoved = {
                            state.eventSink(ChangeRolesEvent.UserSelectionToggled(aRoomMember(it.userId)))
                        },
                        canDeselect = { state.canRemoveMember(aRoomMember(it.userId)) },
                    )
                    SearchResultsList(
                        lazyListState = lazyListState,
                        searchResults = (state.searchResults as? SearchBarResultState.Results)?.results ?: persistentListOf(),
                        selectedUsers = state.selectedUsers,
                        canRemoveMember = state.canRemoveMember,
                        onSelectionToggled = { state.eventSink(ChangeRolesEvent.UserSelectionToggled(it)) },
                    )
                }
            }
        }
    }

    when (state.exitState) {
        is AsyncAction.Confirming -> {
            ConfirmationDialog(
                content = "Some changes were not saved in this screen. Are you sure you want to exit?",
                onSubmitClicked = { state.eventSink(ChangeRolesEvent.Exit) },
                onDismiss = { state.eventSink(ChangeRolesEvent.CancelExit) }
            )
        }
        is AsyncAction.Success -> {
            SideEffect { updatedOnBackPressed() }
        }
        else -> Unit
    }

    when (state.savingState) {
        is AsyncAction.Loading -> {
            ProgressDialog()
        }
        is AsyncAction.Failure -> {
            ErrorDialog(
                content = "We couldn't change the roles of some users",
                onDismiss = { state.eventSink(ChangeRolesEvent.ClearError) }
            )
        }
        is AsyncAction.Success -> {
            LaunchedEffect(Unit) {
                // Give it some time to hide the progress dialog
                delay(100)
                updatedOnBackPressed()
            }
        }
        else -> Unit
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultsList(
    searchResults: ImmutableList<RoomMember>,
    selectedUsers: ImmutableList<MatrixUser>,
    canRemoveMember: (RoomMember) -> Boolean,
    onSelectionToggled: (RoomMember) -> Unit,
    lazyListState: LazyListState,
) {
    LazyColumn(
        state = lazyListState,
    ) {
        stickyHeader {
            Text(
                modifier = Modifier
                    .background(ElementTheme.colors.bgCanvasDefault)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                text = stringResource(CommonStrings.common_search_results),
                style = ElementTheme.typography.fontBodyLgMedium,
            )
        }
        items(searchResults, key = { it.userId }) { roomMember ->
            val canToggle = canRemoveMember(roomMember)
            val trailingContent: @Composable (() -> Unit)? = if (canToggle) {
                {
                    Checkbox(
                        checked = selectedUsers.any { it.userId == roomMember.userId },
                        onCheckedChange = { onSelectionToggled(roomMember) }
                    )
                }
            } else {
                null
            }
            MatrixUserRow(
                modifier = Modifier.clickable(enabled = canToggle, onClick = { onSelectionToggled(roomMember) }),
                matrixUser = MatrixUser(
                    userId = roomMember.userId,
                    displayName = roomMember.displayName,
                    avatarUrl = roomMember.avatarUrl,
                ),
                trailingContent = trailingContent,
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun ChangeRolesAdminViewPreview(@PreviewParameter(ChangeRolesStateProvider::class) state: ChangeRolesState) {
    ElementPreview {
        ChangeRolesView(
            state = state,
            onBackPressed = {},
        )
    }
}
