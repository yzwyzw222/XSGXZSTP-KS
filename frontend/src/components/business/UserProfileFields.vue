<script setup lang="ts">
import { ElInput } from 'element-plus'

import { profileFields, type UserProfileForm } from '@/utils/user-profile'

defineProps<{ prefix: string; requireName?: boolean; disabled?: boolean }>()
const model = defineModel<UserProfileForm>({ required: true })
</script>

<template>
  <div class="grid gap-4 sm:grid-cols-2">
    <div
      v-for="field in profileFields"
      :key="field.key"
      class="grid gap-1.5 text-sm"
      :class="field.key === 'remark' ? 'sm:col-span-2' : ''"
    >
      <label class="font-medium text-muted-foreground" :for="`${prefix}-${field.key}`">
        {{ field.label }}<span v-if="requireName && field.key === 'realName'" class="text-destructive"> *</span>
      </label>
      <ElInput
        v-if="field.key === 'remark'"
        :id="`${prefix}-${field.key}`"
        v-model="model[field.key]"
        type="textarea"
        :maxlength="field.limit"
        :disabled="disabled"
        :rows="3"
        show-word-limit
        resize="vertical"
      />
      <ElInput
        v-else
        :id="`${prefix}-${field.key}`"
        v-model="model[field.key]"
        :type="field.type"
        :maxlength="field.limit"
        :disabled="disabled"
        :required="requireName && field.key === 'realName'"
      />
    </div>
  </div>
</template>
