import { homeAssets } from '../home-assets'
import type { CategoryItem, ShowComingSoon } from '../home-types'
import { FoodImage } from './FoodImage'

interface CategorySectionProps {
  categories: readonly CategoryItem[]
  onComingSoon: ShowComingSoon
}

export function CategorySection({ categories, onComingSoon }: CategorySectionProps) {
  return (
    <section id="categorias" className="category-section" aria-labelledby="titulo-categorias" data-testid="categories">
      <div className="content-shell">
        <h2 id="titulo-categorias" data-keep-clear>¿Qué se te antoja hoy?</h2>
        <div className="category-grid grid grid-cols-2 md:grid-cols-3 xl:grid-cols-6" data-testid="category-grid">
          {categories.map((category) => (
            <button
              key={category.id}
              type="button"
              className="category-card"
              aria-labelledby={'categoria-' + category.id}
              data-action={'categoria-' + category.id}
              onClick={(event) => onComingSoon(event.currentTarget)}
            >
              <span className="category-picture">
                <FoodImage asset={homeAssets[category.imageId]} className="category-image" />
              </span>
              <span className="category-footer">
                <span id={'categoria-' + category.id} className="category-label" data-category-label data-keep-clear>{category.label}</span>
                <span className="category-arrow" aria-hidden="true">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14m-6-6 6 6-6 6" /></svg>
                </span>
              </span>
            </button>
          ))}
        </div>
      </div>
    </section>
  )
}
