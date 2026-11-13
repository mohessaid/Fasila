import "@fontsource-variable/inter";
import "@fontsource/ibm-plex-sans-arabic/400.css";
import "@fontsource/ibm-plex-sans-arabic/500.css";
import "@fontsource/ibm-plex-sans-arabic/600.css";
import "./app.css";
import { mount } from "svelte";
import App from "./App.svelte";

export default mount(App, { target: document.getElementById("app")! });
