import { homeAssets } from '../home-assets'
import { FoodImage } from './FoodImage'

export function HeroFoodComposition() {
  return (
    <div className="food-composition" data-testid="food-composition">
      <div className="food-backdrop bg-mikuna-peach" data-peach-background aria-hidden="true" />
      <FoodImage asset={homeAssets.wings} className="food-wings" priority />
      <div className="food-pizza">
        <FoodImage asset={homeAssets.pizzaWhole} className="composition-image" priority />
      </div>
      <div className="food-burger">
        <FoodImage asset={homeAssets.heroBurger} className="composition-image" priority />
      </div>
    </div>
  )
}
