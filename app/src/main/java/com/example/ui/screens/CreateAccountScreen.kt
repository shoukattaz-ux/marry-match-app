package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.core.PhotoProcessor
import com.example.core.admin.AccountTemplate
import com.example.core.admin.AccountTemplate.Field
import com.example.data.NewAccountPhoto
import com.example.model.PhotoFocus
import com.example.ui.theme.extendedColors
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.CreateAccountState
import kotlinx.coroutines.launch

/**
 * Admin panel → Create: paste the person's details in the template's format, check what was
 * understood, add 3–6 photos and create the account at once (no confirmation e-mail).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAccountScreen(state: CreateAccountState, lang: AppLanguage, vm: AdminViewModel) {
    val t: (String) -> String = { Localization.t(it, lang) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    BackHandler(onBack = vm::closeCreate)

    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = CreateAccountState.MAX_PHOTOS)
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        vm.setPreparingPhotos(true)
        scope.launch {
            var failed = false
            // Prepared exactly like profile photos: upright, 1080 px, small and blurred copies, framing on the face.
            val prepared = uris.mapNotNull { uri ->
                runCatching { PhotoProcessor.prepare(context.applicationContext, uri, withThumbnail = true) }
                    .onFailure { failed = true }
                    .getOrNull()
                    ?.let { p ->
                        val f = p.faceFocus ?: PhotoFocus()
                        NewAccountPhoto(p.jpeg, p.thumb, p.blurred, f.x, f.y, f.zoom, p.faceCount)
                    }
            }
            vm.addCreatePhotos(prepared, failed)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("create_title")) },
                navigationIcon = {
                    IconButton(onClick = vm::closeCreate) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("back")) }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).testTag("create_account_screen"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val created = state.created
            if (created != null) {
                CreatedCard(created.name, created.email, created.password, lang,
                    onCopy = { clipboard.setText(AnnotatedString(String.format(t("create_copy_text"), created.email, created.password))) },
                    onAnother = vm::createAnother,
                    onDone = vm::closeCreate)
                return@Column
            }

            Text(t("create_intro"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.extendedColors.muted)

            // 1. The text
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("create_step_text"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.setCreateText(AccountTemplate.TEMPLATE_EN) }, modifier = Modifier.testTag("create_use_template")) {
                    Text(t("create_use_template"))
                }
            }
            OutlinedTextField(
                value = state.text,
                onValueChange = vm::setCreateText,
                placeholder = { Text(AccountTemplate.TEMPLATE_EN, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                minLines = 14,
                modifier = Modifier.fillMaxWidth().testTag("create_text")
            )
            Text(t("create_format_hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
            OutlinedButton(
                onClick = { vm.checkCreate() },
                enabled = state.text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().testTag("create_check")
            ) { Text(t("create_check")) }

            state.checked?.let { Summary(it, lang) }

            // 2. Photos
            Text(String.format(t("create_step_photos"), state.photos.size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(t("create_photos_hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
            if (state.photos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(state.photos) { i, photo ->
                        Box(Modifier.size(86.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            // Coil decodes the small copy off the main thread.
                            AsyncImage(model = photo.thumb ?: photo.jpeg, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            if (i == 0) {
                                Text(
                                    t("create_main_photo"),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            IconButton(
                                onClick = { vm.removeCreatePhoto(i) },
                                modifier = Modifier.align(Alignment.TopEnd).size(26.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) { Icon(Icons.Default.Close, contentDescription = t("create_remove_photo"), tint = Color.White, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
            }
            // Like the normal profile: the main photo should show exactly one face (a warning only,
            // face detection can miss a good photo; remove and add again to change the order).
            state.photos.firstOrNull()?.faceCount?.takeIf { it != 1 }?.let { faces ->
                Text(
                    t(if (faces == 0) "create_warn_main_no_face" else "create_warn_main_faces"),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB26A00),
                    modifier = Modifier.testTag("create_face_warning")
                )
            }
            if (state.preparingPhotos) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(
                onClick = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = state.photos.size < CreateAccountState.MAX_PHOTOS && !state.preparingPhotos && !state.creating,
                modifier = Modifier.fillMaxWidth().testTag("create_add_photos")
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(t("create_add_photos"))
            }

            // 3. Create
            state.errorKey?.let { key ->
                Text(t(key), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("create_error"))
            }
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(t("create_consent_note"), style = MaterialTheme.typography.bodySmall)
            }
            if (state.creating) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(
                onClick = { vm.createAccount() },
                enabled = state.canCreate,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("create_submit")
            ) { Text(t(if (state.creating) "create_creating" else "create_submit"), fontWeight = FontWeight.Bold) }
            if (!state.canCreate && !state.creating) {
                Text(
                    t(
                        when {
                            state.checked == null -> "create_need_check"
                            state.checked.ok.not() -> "create_need_fix"
                            else -> "create_need_photos"
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.muted
                )
            }
        }
    }
}

/** What the text was understood as: one line per field, errors in red, things left out in orange. */
@Composable
private fun Summary(r: AccountTemplate.Result, lang: AppLanguage) {
    val t: (String) -> String = { Localization.t(it, lang) }
    val errors = r.errors.associateBy { it.field }
    val rows = listOf(
        Field.EMAIL to r.email,
        Field.PASSWORD to r.password,
        Field.NAME to r.name,
        Field.GENDER to r.gender.takeIf { it.isNotEmpty() }?.let { t("create_gender_$it") }.orEmpty(),
        Field.BIRTH_DATE to (r.birthDate?.let { "${it.dayOfMonth.toString().padStart(2, '0')}.${it.monthValue.toString().padStart(2, '0')}.${it.year} (${r.age(java.time.LocalDate.now())})" } ?: ""),
        Field.CITY to listOf(r.city, r.region).filter { it.isNotEmpty() }.distinct().joinToString(", "),
        Field.LANGUAGE to t(if (r.language == "en") "create_lang_en" else "create_lang_de"),
        Field.HEIGHT to (r.heightCm?.let { "$it cm" } ?: ""),
        Field.RELIGION to r.religion,
        Field.EDUCATION to r.education,
        Field.OCCUPATION to r.occupation,
        Field.MARRIAGE to r.marriageTimeline.takeIf { it.isNotEmpty() }?.let { t("timeline_$it") }.orEmpty(),
        Field.CHILDREN to r.wantsChildren.takeIf { it.isNotEmpty() }?.let { t("children_$it") }.orEmpty(),
        Field.LANGUAGES to r.languages.joinToString(", "),
        Field.INTERESTS to r.interests.joinToString(", ") { t("interest_$it") },
        Field.BIO to r.bio
    ) + r.traits.map { (column, value) -> (TRAIT_FIELD[column] ?: Field.SMOKING) to t("${column}_$value") }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (r.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().testTag("create_summary")
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                t(if (r.ok) "create_summary_ok" else "create_summary_fix"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (r.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            rows.forEach { (field, value) ->
                val error = errors[field]
                if (value.isEmpty() && error == null) return@forEach
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        if (error == null) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = if (error == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp).padding(top = 2.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(t("create_field_" + field.name.lowercase()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.extendedColors.muted)
                        if (value.isNotEmpty()) Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
                        error?.let {
                            Text(
                                t(it.code) + (it.value.takeIf { v -> v.isNotEmpty() }?.let { v -> " ($v)" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            if (r.warnings.isNotEmpty()) {
                Text(
                    String.format(t("create_left_out"), r.warnings.joinToString(", ") { it.value }),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB26A00)
                )
            }
        }
    }
}

private val TRAIT_FIELD = mapOf(
    "smoking" to Field.SMOKING, "drinking" to Field.DRINKING, "exercise" to Field.EXERCISE, "diet" to Field.DIET,
    "pets" to Field.PETS, "sleep" to Field.SLEEP, "personality" to Field.PERSONALITY, "family_closeness" to Field.FAMILY,
    "household" to Field.HOUSEHOLD, "home_after_marriage" to Field.HOME, "relocate" to Field.RELOCATE
)

@Composable
private fun CreatedCard(name: String, email: String, password: String, lang: AppLanguage, onCopy: () -> Unit, onAnother: () -> Unit, onDone: () -> Unit) {
    val t: (String) -> String = { Localization.t(it, lang) }
    var copied by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().testTag("create_done")
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(String.format(t("create_done_title"), name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(t("create_done_body"), style = MaterialTheme.typography.bodyMedium)
            Text("${t("create_field_email")}: $email", fontFamily = FontFamily.Monospace)
            Text("${t("create_field_password")}: $password", fontFamily = FontFamily.Monospace)
            Button(onClick = { onCopy(); copied = true }, modifier = Modifier.fillMaxWidth().testTag("create_copy")) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(t(if (copied) "create_copied" else "create_copy"))
            }
            Text(t("create_done_note"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAnother, modifier = Modifier.weight(1f)) { Text(t("create_another")) }
                OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) { Text(t("create_close")) }
            }
        }
    }
}
