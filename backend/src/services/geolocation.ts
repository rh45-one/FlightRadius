// TODO: Implement GPS or device geolocation provider abstraction.
export const getCurrentLocation = async () => {
  return {
    latitude: null,
    longitude: null,
    source: "unimplemented"
  };
};
