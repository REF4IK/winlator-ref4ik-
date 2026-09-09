package com.winlator.cmod.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.winlator.cmod.R

/** Круглая аватарка аккаунта. Без URL — иконка-заглушка. */
@Composable
fun AccountAvatar(
    avatarUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    fallbackTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (avatarUrl.isNullOrBlank()) {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            tint = fallbackTint,
            modifier = modifier.size(size),
        )
    } else {
        // Иконка с тинтом снизу (видна пока грузится и при битой картинке),
        // фото сверху. Плейсхолдер Coil без тинта был бы чёрным в тёмной теме.
        androidx.compose.foundation.layout.Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = fallbackTint,
                modifier = Modifier.size(size),
            )
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = null,
                error = null,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        }
    }
}
/** Плашка OWNER рядом с ником админа. */
@Composable
fun OwnerBadge(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier,
    ) {
        Text(
            stringResource(R.string.owner_badge),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}
