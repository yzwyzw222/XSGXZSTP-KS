import { z } from 'zod'

const nodeType = z.enum(['ACHIEVEMENT', 'AUTHOR', 'INSTITUTION', 'VENUE', 'TOPIC'])
const integerText = (minimum: number, maximum: number) => z.string().regex(/^\d+$/)
  .refine((value) => Number.isSafeInteger(Number(value)) && Number(value) >= minimum && Number(value) <= maximum)
const year = z.union([z.literal(''), integerText(1000, 9999)])

export const graphFilterSchema = z.object({
  centerType: nodeType,
  centerId: integerText(1, Number.MAX_SAFE_INTEGER),
  depth: integerText(1, 2),
  nodeLimit: integerText(1, 300),
  publicationYearFrom: year,
  publicationYearTo: year,
  nodeTypes: z.array(nodeType).max(5),
  relationshipTypes: z.array(z.enum(['AUTHORED', 'AFFILIATED_WITH', 'PUBLISHED_IN', 'HAS_TOPIC', 'CITES'])).max(5),
  achievementTypes: z.string().max(200),
}).refine((value) => !value.publicationYearFrom || !value.publicationYearTo
  || Number(value.publicationYearFrom) <= Number(value.publicationYearTo))

export type GraphFilters = z.infer<typeof graphFilterSchema>
export const savedGraphQuerySchema = z.array(z.object({
  name: z.string().trim().min(1).max(40),
  filters: graphFilterSchema,
})).max(10)
export type SavedGraphQuery = z.infer<typeof savedGraphQuerySchema>[number]
