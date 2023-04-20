import {
  requireNativeComponent,
  UIManager,
  Platform,
  ViewStyle,
} from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-barcode' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

type BarcodeProps = {
  color: string;
  style: ViewStyle;
};

const ComponentName = 'BarcodeView';

export const BarcodeView =
  UIManager.getViewManagerConfig(ComponentName) != null
    ? requireNativeComponent<BarcodeProps>(ComponentName)
    : () => {
        throw new Error(LINKING_ERROR);
      };
