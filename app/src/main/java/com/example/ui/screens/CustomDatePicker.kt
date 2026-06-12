package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CustomDatePickerDialogUI(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var currentMonth by remember { mutableStateOf(YearMonth.from(initialDate)) }
    var selectedDate by remember { mutableStateOf(initialDate) }

    var currentStep by remember { mutableStateOf(0) } // 0: Year, 1: Month, 2: Day

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Date
        Row(
            modifier = Modifier.padding(bottom = 32.dp, top = 24.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            val monthStr = selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM"))
            val dayStr = selectedDate.dayOfMonth.toString()
            val yearStr = selectedDate.year.toString()

            Text(
                text = monthStr,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = if (currentStep == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable { 
                    currentMonth = YearMonth.from(selectedDate)
                    currentStep = 1 
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = dayStr,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = if (currentStep == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable { 
                    currentMonth = YearMonth.from(selectedDate)
                    currentStep = 2 
                }
            )
            Text(
                text = ", ",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = yearStr,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = if (currentStep == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable { 
                    currentMonth = YearMonth.from(selectedDate)
                    currentStep = 0 
                }
            )
        }

        // Calendar Area Header (Back button and current selection title)
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentStep > 0) {
                IconButton(
                    onClick = {
                        currentStep--
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
            
            val titleText = when (currentStep) {
                0 -> "Select Year"
                1 -> "Select Month"
                else -> "Select Date"
            }
            
            Text(
                text = titleText,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.width(48.dp))
        }

        Box(modifier = Modifier.height(280.dp).fillMaxWidth()) {
            AnimatedContent(
                targetState = currentStep,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "step_transition"
            ) { step ->
                when (step) {
                    0 -> {
                    val currentYearForLimit = LocalDate.now().year
                    val years = (2020..(currentYearForLimit + 20)).toList()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(years) { yearValue ->
                            val isSelected = currentMonth.year == yearValue
                            val isPast = yearValue < currentYearForLimit
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .then(if (isPast) Modifier else Modifier.clickable {
                                        currentMonth = currentMonth.withYear(yearValue)
                                        
                                        // Update selectedDate's year to match selection
                                        // Since withYear can result in an invalid day (e.g. Feb 29 on non-leap year), 
                                        // we determine valid day dynamically, or use withYear directly which handles this.
                                        selectedDate = selectedDate.withYear(yearValue)
                                        
                                        currentStep = 1
                                    })
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = yearValue.toString(),
                                    color = if (isPast) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                1 -> {
                    val today = LocalDate.now()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(12) { index ->
                            val monthValue = index + 1
                            val isSelected = currentMonth.monthValue == monthValue
                            val isPast = currentMonth.year == today.year && monthValue < today.monthValue || currentMonth.year < today.year
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .then(if (isPast) Modifier else Modifier.clickable {
                                        currentMonth = currentMonth.withMonth(monthValue)
                                        
                                        // Update selectedDate's month to match selection
                                        // Determine valid day dynamically.
                                        val maxDay = currentMonth.lengthOfMonth()
                                        val newDay = minOf(selectedDate.dayOfMonth, maxDay)
                                        selectedDate = selectedDate.withMonth(monthValue).withDayOfMonth(newDay)
                                        
                                        currentStep = 2
                                    })
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = java.time.Month.of(monthValue).getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                    color = if (isPast) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                2 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Days of week
                        Row(modifier = Modifier.fillMaxWidth()) {
                            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                                Text(
                                    text = day,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Grid of days
                        val firstDayOfWeek = currentMonth.atDay(1).dayOfWeek.value % 7 // Sunday = 0
                        val daysInMonth = currentMonth.lengthOfMonth()
                        
                        val daysList = mutableListOf<Int?>()
                        repeat(firstDayOfWeek) { daysList.add(null) }
                        (1..daysInMonth).forEach { daysList.add(it) }

                        val today = LocalDate.now()

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(7),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(daysList) { day ->
                                if (day == null) {
                                    Box(modifier = Modifier.aspectRatio(1f))
                                } else {
                                    val isSelected = selectedDate.year == currentMonth.year && 
                                                     selectedDate.month == currentMonth.month && 
                                                     selectedDate.dayOfMonth == day
                                    val isPast = (currentMonth.year == today.year && 
                                                 currentMonth.monthValue == today.monthValue && 
                                                 day < today.dayOfMonth) ||
                                                 (currentMonth.year == today.year && currentMonth.monthValue < today.monthValue) ||
                                                 (currentMonth.year < today.year)
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .padding(2.dp)
                                            .background(
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                shape = androidx.compose.foundation.shape.CircleShape
                                            )
                                            .then(if (isPast) Modifier else Modifier.clickable {
                                                selectedDate = currentMonth.atDay(day)
                                            }),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = day.toString(),
                                            color = if (isPast) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = { 
                onDateSelected(selectedDate) 
            }) {
                Text("OK", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
