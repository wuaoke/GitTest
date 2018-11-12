package com.dalimao.mytaxi.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps2d.AMap;
import com.amap.api.maps2d.CameraUpdate;
import com.amap.api.maps2d.CameraUpdateFactory;
import com.amap.api.maps2d.LocationSource;
import com.amap.api.maps2d.MapView;
import com.amap.api.maps2d.model.BitmapDescriptor;
import com.amap.api.maps2d.model.BitmapDescriptorFactory;
import com.amap.api.maps2d.model.CameraPosition;
import com.amap.api.maps2d.model.LatLng;
import com.amap.api.maps2d.model.Marker;
import com.amap.api.maps2d.model.MarkerOptions;
import com.amap.api.maps2d.model.MyLocationStyle;
import com.dalimao.mytaxi.map.bean.LocationInfo;
import com.dalimao.mytaxi.util.SensorEventHelper;

import java.util.HashMap;
import java.util.Map;

public class GaoDeMapLayerImpl implements IMapLayer {
    private Context mContext;
    private MapView mapView;
    private AMap aMap;
    private AMapLocationClient mlocationClient;
    private AMapLocationClientOption mLocationOption;
    private LocationSource.OnLocationChangedListener mMapLocationChangeListener;
    private String KEY_MY_MARKERE = "100";
    private CommonLocationChangeListener mLocationChangeListener;
    private MyLocationStyle myLocationStyle;
    private SensorEventHelper mSensorHelper;
    // 管理地图标记集合
    private Map<String, Marker> markerMap = new HashMap<>();
    private LocationSource.OnLocationChangedListener mListener;
    private boolean mFirstFix = true;

    public GaoDeMapLayerImpl(Context context) {
        this.mContext = context;
        mapView = new MapView(mContext);
        aMap = mapView.getMap();
    }

    @Override
    public View getMapView() {
        return mapView;
    }

    @Override
    public void setLocationChangeListener(CommonLocationChangeListener listener) {
        this.mLocationChangeListener = listener;
    }

    @Override
    public void setLocationRes(int res) {
        // 自定义系统定位小蓝点
        myLocationStyle = new MyLocationStyle();
        myLocationStyle.myLocationIcon(BitmapDescriptorFactory
                .fromResource(res));// 设置小蓝点的图标//R.drawable.location_marker

        myLocationStyle.strokeColor(Color.TRANSPARENT);// 设置圆形的边框颜色
        myLocationStyle.radiusFillColor(Color.TRANSPARENT);// 设置圆形的填充颜色
        // myLocationStyle.anchor(int,int)//设置小蓝点的锚点
        //myLocationStyle.strokeWidth(0f);// 设置圆形的边框粗细
        aMap.setMyLocationStyle(myLocationStyle);
    }

    @Override
    public void addOrUpdateMarker(LocationInfo locationInfo, Bitmap bitmap) {
        Marker storedMarker = markerMap.get(locationInfo.key);
        BitmapDescriptor des = BitmapDescriptorFactory.fromBitmap(bitmap);
        if (storedMarker != null) {
            storedMarker.setPosition(new LatLng(locationInfo.latitude, locationInfo.longitude));
            storedMarker.setRotateAngle(locationInfo.rotation);
        } else {
            MarkerOptions options = new MarkerOptions();
            options.icon(des);
            options.anchor(0.5f, 0.5f);
            options.position(new LatLng(locationInfo.latitude, locationInfo.longitude));
            Marker marker = aMap.addMarker(options);
            marker.setRotateAngle(locationInfo.rotation);
            markerMap.put(locationInfo.key, marker);
            if (mSensorHelper != null && locationInfo.key.equals(KEY_MY_MARKERE))
                mSensorHelper.setCurrentMarker(marker);//定位图标旋转
        }

    }

    @Override
    public void onCreate(Bundle state) {
        mapView.onCreate(state);
        init();
    }

    /**
     * 初始化
     */
    private void init() {
        if (aMap == null) {
            aMap = mapView.getMap();
        }
        setUpMap();
        mSensorHelper = new SensorEventHelper(mContext);
        if (mSensorHelper != null) {
            mSensorHelper.registerSensorListener();
        }
    }

    /**
     * 设置一些amap的属性
     */
    private void setUpMap() {
        aMap.setLocationSource(new LocationSource() {
            @Override
            public void activate(OnLocationChangedListener listener) {
                mListener = listener;
                if (mlocationClient == null) {
                    mlocationClient = new AMapLocationClient(mContext);
                    mLocationOption = new AMapLocationClientOption();
                    //设置定位监听
                    mlocationClient.setLocationListener(new AMapLocationListener() {
                        @Override
                        public void onLocationChanged(AMapLocation amapLocation) {
                            if (mListener != null && amapLocation != null) {
                                if (amapLocation != null && amapLocation.getErrorCode() == 0) {
                                    mListener.onLocationChanged(amapLocation);// 显示系统小蓝点
                                    CameraUpdate up = CameraUpdateFactory
                                            .newCameraPosition(new CameraPosition(new LatLng(amapLocation.getLatitude(), amapLocation.getLongitude()),
                                                    18, 30, 0));
                                    aMap.moveCamera(up);
                                    LocationInfo location = new LocationInfo(amapLocation.getLatitude(), amapLocation.getLongitude());
                                    location.key = KEY_MY_MARKERE;
                                    if (mFirstFix) {
                                        mFirstFix = false;
                                        if (mLocationChangeListener != null)
                                            mLocationChangeListener.onLocation(location);

                                    } else {

                                        if (mLocationChangeListener != null)
                                            mLocationChangeListener.onLocationChanged(location);
                                    }

                                } else {
                                    String errText = "定位失败," + amapLocation.getErrorCode() + ": " + amapLocation.getErrorInfo();
                                    Log.e("AmapErr", errText);

                                }
                            }

                        }
                    });
                    //设置为高精度定位模式
                    mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
                    //设置定位参数
                    mlocationClient.setLocationOption(mLocationOption);
                    // 此方法为每隔固定时间会发起一次定位请求，为了减少电量消耗或网络流量消耗，
                    // 注意设置合适的定位时间的间隔（最小间隔支持为2000ms），并且在合适时间调用stopLocation()方法来取消定位请求
                    // 在定位结束后，在合适的生命周期调用onDestroy()方法
                    // 在单次定位情况下，定位无论成功与否，都无需调用stopLocation()方法移除请求，定位sdk内部会移除
                    mlocationClient.startLocation();
                }
            }

            @Override
            public void deactivate() {
                mListener = null;
                if (mlocationClient != null) {
                    mlocationClient.stopLocation();
                    mlocationClient.onDestroy();
                }
                mlocationClient = null;

            }
        });// 设置定位监听
        aMap.getUiSettings().setMyLocationButtonEnabled(true);// 设置默认定位按钮是否显示
        aMap.setMyLocationEnabled(true);// 设置为true表示显示定位层并可触发定位，false表示隐藏定位层并不可触发定位，默认是false
    }

    @Override
    public void onResume() {
        mapView.onResume();
        if (mSensorHelper != null) {
            mSensorHelper.registerSensorListener();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onPause() {
        mapView.onPause();
        mListener = null;
        if (mlocationClient != null) {
            mlocationClient.stopLocation();
            mlocationClient.onDestroy();
        }
        if (mSensorHelper != null) {
            mSensorHelper.unRegisterSensorListener();
            mSensorHelper.setCurrentMarker(null);
            mSensorHelper = null;
        }
        mlocationClient = null;
    }

    @Override
    public void onDestroy() {
        mapView.onDestroy();
        if (null != mlocationClient) {
            mlocationClient.onDestroy();
        }
        mSensorHelper.unRegisterSensorListener();
    }
}