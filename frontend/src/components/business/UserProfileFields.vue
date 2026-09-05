<script setup lang="ts">
import { FormItem, FormLabel } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { profileFields, type UserProfileForm } from '@/utils/user-profile'

defineProps<{ prefix: string; requireName?: boolean; disabled?: boolean }>()
const model = defineModel<UserProfileForm>({ required: true })
</script>

<template>
  <div class="grid gap-4 sm:grid-cols-2">
    <FormItem v-for="field in profileFields" :key="field.key" :class="field.key === 'remark' ? 'sm:col-span-2' : ''">
      <FormLabel :for="`${prefix}-${field.key}`">{{ field.label }}<span v-if="requireName && field.key === 'realName'" class="text-destructive"> *</span></FormLabel>
      <Textarea v-if="field.key === 'remark'" :id="`${prefix}-${field.key}`" v-model="model[field.key]" :maxlength="field.limit" :disabled="disabled" :rows="3" />
      <Input v-else :id="`${prefix}-${field.key}`" v-model="model[field.key]" :type="field.type" :maxlength="field.limit" :disabled="disabled" :required="requireName && field.key === 'realName'" />
    </FormItem>
  </div>
</template>
