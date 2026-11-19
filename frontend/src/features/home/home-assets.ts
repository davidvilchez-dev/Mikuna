import type { HomeAssetId, VisualAsset } from './home-types'
import logoImage from '../../assets/branding/logo-mikuna.svg'
import wingsImage from '../../assets/home/wings.png'
import pizzaWholeImage from '../../assets/home/pizza-whole.png'
import heroBurgerImage from '../../assets/home/burger.png'
import categoryBurgerImage from '../../assets/home/category-burger.png'
import chickenImage from '../../assets/home/chicken.png'
import pizzaSliceImage from '../../assets/home/pizza-slice.png'
import peruvianFoodImage from '../../assets/home/peruvian-food.png'
import dessertImage from '../../assets/home/dessert.png'
import drinkImage from '../../assets/home/drink.png'

// Copias íntegras de los recursos aportados para el inicio público.
// Los originales permanecen en docs/diseno y docs/seis_categorías.
export const homeAssets = {
  logo: {
    id: 'logo',
    src: logoImage,
    alternativeText: 'Mikuna',
    width: 3922,
    height: 815,
    provenance: 'Vector trazado de docs/diseno/logo-mikuna.png',
    sha256: '6EF9998EFDB7A393B074FC3DBF56D7D537F5AA134A6756CD6ACED5435BD14C2E',
  },
  wings: {
    id: 'wings',
    src: wingsImage,
    alternativeText: 'Plato de alitas BBQ con salsa para acompañar',
    width: 1536,
    height: 1024,
    provenance: 'docs/diseno/Plato de alitas BBQ y dip-1.png',
    sha256: '8903E4B5428F9AF4CABB205F94A0FB9EAC95279CC6CA7D99CE5B76C9B557F949',
  },
  pizzaWhole: {
    id: 'pizzaWhole',
    src: pizzaWholeImage,
    alternativeText: 'Pizza entera de pepperoni',
    width: 1254,
    height: 1254,
    provenance: 'docs/diseno/Pizza redonda de pepperoni dorada-3.png',
    sha256: 'F6A5A7564B8B7FAD0CD84574B9BAA979F94CE12CC38B56FB35713F33A2905D49',
  },
  heroBurger: {
    id: 'heroBurger',
    src: heroBurgerImage,
    alternativeText: 'Hamburguesa con queso, lechuga y tomate',
    width: 1254,
    height: 1254,
    provenance: 'docs/diseno/Hamburguesa gourmet con queso fundido.png',
    sha256: '1B173C5504E412D5DC8541F3F7E796AE5CE2F71641C380E02FA5EE411CC1D229',
  },
  categoryBurger: {
    id: 'categoryBurger',
    src: categoryBurgerImage,
    alternativeText: 'Hamburguesa clásica con pan de sésamo',
    width: 1254,
    height: 1254,
    provenance: 'docs/seis_categorías/Hamburguesa clásica con sésamo-1.png',
    sha256: 'C2BFBCF8F14CD9CBAD90FCF9B917B3B694495A93A6E45101EDFE6D8B2AA7A120',
  },
  chicken: {
    id: 'chicken',
    src: chickenImage,
    alternativeText: 'Porciones de pollo frito crujiente',
    width: 1254,
    height: 1254,
    provenance: 'docs/seis_categorías/Pila de pollo frito crujiente-2.png',
    sha256: '32C0A7288274B9C0EE22C25EFF64A706866BC64B28782EF0C4CEF8A10C531C10',
  },
  pizzaSlice: {
    id: 'pizzaSlice',
    src: pizzaSliceImage,
    alternativeText: 'Porción de pizza de pepperoni',
    width: 1254,
    height: 1254,
    provenance: 'docs/seis_categorías/Porción de pizza dorada con pepperoni-2.png',
    sha256: '566D8FEC30EA41478FF27EA2E1D5B8438AB9EFA917060145BAC995E93CED4D3C',
  },
  peruvianFood: {
    id: 'peruvianFood',
    src: peruvianFoodImage,
    alternativeText: 'Plato de ceviche peruano con camote y choclo',
    width: 1254,
    height: 1254,
    provenance: 'docs/seis_categorías/Plato de ceviche peruano-3.png',
    sha256: '59C8A06C8629757124E474D11FCCF28A88B9332471A53601C8FD4D8ADA4DBB21',
  },
  dessert: {
    id: 'dessert',
    src: dessertImage,
    alternativeText: 'Porción de pastel de chocolate',
    width: 1254,
    height: 1254,
    provenance: 'docs/seis_categorías/Rebanada de pastel de chocolate.png',
    sha256: '5EE58ECE5D22D17EF9CC439F3CAEFB752A6848E853CE728FF36BEEF754F89234',
  },
  drink: {
    id: 'drink',
    src: drinkImage,
    alternativeText: 'Vaso de maracuyá acompañado de la fruta',
    width: 1254,
    height: 1254,
    provenance: 'docs/seis_categorías/Vaso de maracuyá con fruta-3.png',
    sha256: '912FFA007E89280CAE43D9352AD1E539D115FAEDE64EBC861DF299FDB6CB5AE2',
  },
} satisfies Record<HomeAssetId, VisualAsset>
