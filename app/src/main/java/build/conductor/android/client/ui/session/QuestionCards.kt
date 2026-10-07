package build.conductor.android.client.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import build.conductor.android.client.data.transcript.Question
import build.conductor.android.client.data.transcript.QuestionAnswer
import build.conductor.android.client.data.transcript.QuestionOption
import build.conductor.android.client.data.transcript.TranscriptItem
import build.conductor.android.client.data.transcript.describeQuestionResult

/** The form for the question that the agent waits on. */
class QuestionForm(
    val answers: List<QuestionAnswer>,
    val isSending: Boolean,
    val onToggle: (questionIndex: Int, label: String) -> Unit,
    val onOtherTextChange: (questionIndex: Int, text: String) -> Unit,
    val onSend: () -> Unit,
)

@Composable
fun OpenQuestionsCard(item: TranscriptItem.Questions, form: QuestionForm) {
    QuestionsSurface(isOpen = true) {
        Text("The agent asks you", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        item.questions.forEachIndexed { index, question ->
            OpenQuestion(question, form.answers.getOrElse(index) { QuestionAnswer() }, form, index)
        }
        Button(onClick = form.onSend, enabled = form.answers.any { it.isAnswered } && !form.isSending, modifier = Modifier.align(Alignment.End)) {
            Text("Send answers")
        }
    }
}

@Composable
fun ClosedQuestionsCard(item: TranscriptItem.Questions) {
    QuestionsSurface(isOpen = false) {
        Text("The agent asked", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        item.questions.forEach { question ->
            QuestionHeading(question)
            Text(question.options.joinToString(" · ") { it.label }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item.result?.let { Text(describeQuestionResult(it), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun QuestionsSurface(isOpen: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val colors = if (isOpen) CardDefaults.cardColors() else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    Card(colors = colors, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun QuestionHeading(question: Question) {
    question.header?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Text(question.text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun OpenQuestion(question: Question, answer: QuestionAnswer, form: QuestionForm, questionIndex: Int) {
    QuestionHeading(question)
    if (question.isMultiSelect) Text("Choose one or more.", style = MaterialTheme.typography.bodySmall)
    Column(modifier = Modifier.selectableGroup()) {
        question.options.forEach { option ->
            OptionRow(option, isSelected = option.label in answer.selectedLabels, isMultiSelect = question.isMultiSelect) {
                form.onToggle(questionIndex, option.label)
            }
        }
    }
    OutlinedTextField(
        value = answer.otherText,
        onValueChange = { form.onOtherTextChange(questionIndex, it) },
        label = { Text("Other") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun OptionRow(option: QuestionOption, isSelected: Boolean, isMultiSelect: Boolean, onClick: () -> Unit) {
    val selection = if (isMultiSelect) {
        Modifier.toggleable(value = isSelected, role = Role.Checkbox, onValueChange = { onClick() })
    } else {
        Modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
    }
    Row(modifier = Modifier.fillMaxWidth().then(selection).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isMultiSelect) Checkbox(checked = isSelected, onCheckedChange = null) else RadioButton(selected = isSelected, onClick = null)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(option.label, style = MaterialTheme.typography.bodyMedium)
            option.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
