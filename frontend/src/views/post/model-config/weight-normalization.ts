const roundToTwoDecimals = (value: number) => Math.round(value * 100) / 100

export function isLegacyRelativeWeights(weights: number[]): boolean {
  const total = weights.reduce((sum, weight) => sum + (Number.isFinite(weight) ? weight : 0), 0)
  return weights.length > 0
    && weights.every(weight => Number.isFinite(weight) && weight >= 0)
    && weights.some(weight => weight > 0)
    && (total < 95 || total > 105)
}

export function normalizeLegacyRelativeWeights(weights: number[]): number[] {
  if (!isLegacyRelativeWeights(weights)) return weights

  const total = weights.reduce((sum, weight) => sum + weight, 0)
  let allocated = 0
  return weights.map((weight, index) => {
    if (index === weights.length - 1) return roundToTwoDecimals(100 - allocated)
    const normalized = roundToTwoDecimals((weight / total) * 100)
    allocated += normalized
    return normalized
  })
}
