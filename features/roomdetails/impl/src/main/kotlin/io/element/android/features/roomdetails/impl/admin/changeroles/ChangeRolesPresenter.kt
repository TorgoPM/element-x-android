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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.element.android.features.roomdetails.impl.members.PowerLevelRoomMemberComparator
import io.element.android.features.roomdetails.impl.members.RoomMemberListDataSource
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.designsystem.theme.components.SearchBarResultState
import io.element.android.libraries.matrix.api.room.MatrixRoom
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.room.RoomMembershipState
import io.element.android.libraries.matrix.api.user.MatrixUser
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ChangeRolesPresenter @AssistedInject constructor(
    @Assisted private val role: RoomMember.Role,
    private val room: MatrixRoom,
    private val dispatchers: CoroutineDispatchers,
) : Presenter<ChangeRolesState> {
    @AssistedFactory
    interface Factory {
        fun create(role: RoomMember.Role): ChangeRolesPresenter
    }

    @Composable
    override fun present(): ChangeRolesState {
        val coroutineScope = rememberCoroutineScope()
        val dataSource = remember { RoomMemberListDataSource(room, dispatchers) }
        var query by rememberSaveable { mutableStateOf<String?>(null) }
        var searchActive by rememberSaveable { mutableStateOf(false) }
        var searchResults by remember {
            mutableStateOf<SearchBarResultState<ImmutableList<RoomMember>>>(SearchBarResultState.Initial())
        }
        var selectedUsers by remember {
            mutableStateOf<ImmutableList<MatrixUser>>(persistentListOf())
        }
        var initialSelected by remember {
            mutableStateOf<ImmutableList<MatrixUser>>(persistentListOf())
        }
        val exitState: MutableState<AsyncAction<Unit>> = remember { mutableStateOf(AsyncAction.Uninitialized) }
        val saveState: MutableState<AsyncAction<Unit>> = remember { mutableStateOf(AsyncAction.Uninitialized) }

        // Load initial selected items
        LaunchedEffect(Unit) {
            val results = dataSource.search(query.orEmpty()).filter { it.role == role }
            initialSelected = results.map { it.toMatrixUser() }.toImmutableList()
            selectedUsers = initialSelected
        }

        // Update search results for every query change
        LaunchedEffect(query) {
            val results = dataSource
                .search(query.orEmpty())
                .filter { it.membership == RoomMembershipState.JOIN }
                .sorted()

            searchResults = if (results.isEmpty()) {
                SearchBarResultState.NoResultsFound()
            } else {
                SearchBarResultState.Results(results)
            }
        }

        val hasPendingChanges = initialSelected != selectedUsers

        fun handleEvent(event: ChangeRolesEvent) {
            when (event) {
                is ChangeRolesEvent.ToggleSearchActive -> {
                    searchActive = !searchActive
                }
                is ChangeRolesEvent.QueryChanged -> {
                    query = event.query
                }
                is ChangeRolesEvent.UserSelectionToggled -> {
                    val newList = selectedUsers.toMutableList()
                    val index = newList.indexOfFirst { it.userId == event.roomMember.userId }
                    if (index >= 0) {
                        newList.removeAt(index)
                    } else {
                        newList.add(event.roomMember.toMatrixUser())
                    }
                    selectedUsers = newList.toImmutableList()
                }
                is ChangeRolesEvent.Save -> coroutineScope.save(initialSelected, selectedUsers, saveState)
                is ChangeRolesEvent.ClearError -> {
                    saveState.value = AsyncAction.Uninitialized
                }
                is ChangeRolesEvent.Exit -> {
                    exitState.value = if (exitState.value.isUninitialized()) {
                        if (hasPendingChanges) {
                            // Has pending changes, confirm exit
                            AsyncAction.Confirming
                        } else {
                            // No pending changes, exit immediately
                            AsyncAction.Success(Unit)
                        }
                    } else {
                        // Confirming exit
                        AsyncAction.Success(Unit)
                    }
                }
                is ChangeRolesEvent.CancelExit -> {
                    exitState.value = AsyncAction.Uninitialized
                }
            }
        }
        return ChangeRolesState(
            role = role,
            query = query,
            isSearchActive = searchActive,
            searchResults = searchResults,
            selectedUsers = selectedUsers,
            hasPendingChanges = hasPendingChanges,
            exitState = exitState.value,
            savingState = saveState.value,
            canRemoveMember = ::canRemoveMember,
            eventSink = ::handleEvent,
        )
    }

    private fun canRemoveMember(roomMember: RoomMember): Boolean {
        return roomMember.userId == room.sessionId && roomMember.role == RoomMember.Role.ADMIN || // An admin can always change their own role
            roomMember.role != RoomMember.Role.ADMIN // An admin can't remove or demote another admin
    }

    private fun Iterable<RoomMember>.sorted(): ImmutableList<RoomMember> {
        return sortedWith(PowerLevelRoomMemberComparator()).toImmutableList()
    }

    private fun RoomMember.toMatrixUser() = MatrixUser(
        userId = userId,
        displayName = displayName,
        avatarUrl = avatarUrl,
    )

    private fun CoroutineScope.save(
        initialSelected: ImmutableList<MatrixUser>,
        selectedUsers: ImmutableList<MatrixUser>,
        saveState: MutableState<AsyncAction<Unit>>,
    ) = launch {
        saveState.value = AsyncAction.Loading

        val toAdd = selectedUsers - initialSelected
        val toRemove = initialSelected - selectedUsers

        val errors = mutableListOf<Throwable>()
        for (selectedUser in toAdd) {
            room.updateUserRole(selectedUser.userId, role).onFailure { errors.add(it) }
        }
        for (selectedUser in toRemove) {
            room.updateUserRole(selectedUser.userId, RoomMember.Role.USER).onFailure { errors.add(it) }
        }

        if (errors.isEmpty()) {
            saveState.value = AsyncAction.Success(Unit)
        } else {
            saveState.value = AsyncAction.Failure(errors.first())
        }
    }
}
