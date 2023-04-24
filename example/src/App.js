import * as React from 'react';
import {  useState, useEffect,useRef} from 'react';

import { StyleSheet, Text, View } from 'react-native';
import { Camera,useCameraDevices } from 'react-native-barcode';

export default function App() {
  const cameraRef=useRef()
  const cameraPosition = 'back';
  // camera format settings
  const devices = useCameraDevices();
  const device = devices[cameraPosition];
  const [permission,setPermission]=useState(false)
  useEffect(async()=>{
     Camera.getCameraPermissionStatus().then(result=>{
      if('authorized'==result){
          setPermission(true)
      }else{
        Camera.requestCameraPermission().then(result=>{
          setPermission(true)
        })
      }
     });
    return ()=>{}
  },[])
  return (
    <View style={styles.container}>
      <Text>camera test</Text>
      <View style={{width:500,height:400,borderColor:'red',borderWidth:1}}>
        {
          device!=null?<>
            {
              permission==true?
              <Camera 
                style={StyleSheet.absoluteFill}
                ref={cameraRef}
                device={device||{}} 
                isActive={true} 
                enableFrameProcessor={true}
                onInitialized={()=>{
                  console.log('::onInitialized........')
                }}
                onError={(err)=>console.log("错误：",err)}
                onBarcodeScaned={(barcode)=>{
                    console.log('barcode::::',barcode)
                }}
              />:<Text>没有权限</Text>
            }
          </>
          :<Text>没有摄像头!</Text>
          
        }
       
      </View>
      
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  box: {
    width: 60,
    height: 60,
    marginVertical: 20,
  },
});
