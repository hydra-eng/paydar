package com.paydar.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AccountCard() {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val manager = remember { CredentialManager.create(context) }
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(auth.currentUser) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    suspend fun signIn(reauthenticate: Boolean = false) {
        val option = GetSignInWithGoogleOption.Builder(context.getString(R.string.default_web_client_id)).build()
        val result = manager.getCredential(context, GetCredentialRequest.Builder().addCredentialOption(option).build())
        val token = GoogleIdTokenCredential.createFrom(result.credential.data).idToken
        val credential = GoogleAuthProvider.getCredential(token, null)
        if (reauthenticate) auth.currentUser?.reauthenticate(credential)?.await()
        else auth.signInWithCredential(credential).await()
    }
    fun perform(action: suspend () -> Unit) {
        scope.launch {
            busy = true; message = null
            try { action() }
            catch (_: GetCredentialCancellationException) { message = "Sign-in cancelled. Your local tools remain available." }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "Unable to complete this request. Check your connection and Google account, then try again." }
            finally { busy = false }
        }
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("YOUR PAYDAR ACCOUNT", style = MaterialTheme.typography.labelMedium)
            Text(user?.displayName ?: "Continue with Google", style = MaterialTheme.typography.headlineSmall)
            Text(user?.email ?: "Sign in securely with your Google account. You can also keep using PayDar without an account.")
            if (user != null) {
                Text("Google account · " + if (user?.isEmailVerified == true) "Email verified" else "Email not verified", style = MaterialTheme.typography.labelMedium)
                Text("Account ID", style = MaterialTheme.typography.labelSmall)
                Text(user!!.uid, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = !busy, onClick = { perform { auth.signOut(); manager.clearCredentialState(ClearCredentialStateRequest()) } }) { Text("Sign out") }
                TextButton(enabled = !busy, onClick = { confirmDelete = true }) { Text("Delete cloud account") }
            } else {
                Button(enabled = !busy, onClick = { perform { signIn() } }, modifier = Modifier.fillMaxWidth()) { Text("Continue with Google") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("Google and Firebase process your account identity. Payment receipts, SMS analysis and checks stay in this phone’s private storage. Accounts do not sync or separate local records.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete your PayDar account?") },
        text = { Text("Confirm your Google identity to permanently remove your Firebase account. This does not delete your Google account or this phone’s payment records. Use Delete local data separately.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; perform { signIn(true); auth.currentUser?.delete()?.await(); manager.clearCredentialState(ClearCredentialStateRequest()); message = "Cloud account deleted." } }) { Text("Confirm identity and delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
    )
}
