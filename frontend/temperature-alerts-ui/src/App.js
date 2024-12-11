import React, { useEffect, useState } from "react";
import ReactPaginate from "react-paginate";
import connectWebSocket from "./websocket";
import { Bar } from "react-chartjs-2";
import "./App.css";

function App() {
  const [alerts, setAlerts] = useState([]);
  const [searchTerm, setSearchTerm] = useState("");
  const [filteredAlerts, setFilteredAlerts] = useState([]);
  const [currentPage, setCurrentPage] = useState(0);

  const alertsPerPage = 5; // Number of alerts shown per page

  // WebSocket connection effect
  useEffect(() => {
    const stompClient = connectWebSocket((message) => {
      console.log("Message received from server:", message);
      try {
        const parsedMessage = JSON.parse(message);
        setAlerts((prev) => [parsedMessage, ...prev]); // Parse and add to the state
      } catch (e) {
        console.error("Invalid message format:", message);
      }
    });

    return () => {
      if (stompClient && typeof stompClient.deactivate === "function") {
        stompClient.deactivate();
        console.log("WebSocket disconnected");
      } else {
        console.error(
            "stompClient is not properly initialized or does not have a deactivate method"
        );
      }
    };
  }, []);

  // Filter alerts based on search term
  useEffect(() => {
    const filtered = alerts.filter((alert) =>
        JSON.stringify(alert)
            .toLowerCase()
            .includes(searchTerm.toLowerCase())
    );
    setFilteredAlerts(filtered); // Update filtered alerts state
    setCurrentPage(0); // Reset to first page on new search
  }, [searchTerm, alerts]);

  // Handle pagination
  const handlePageClick = (data) => {
    setCurrentPage(data.selected);
  };

  // Extract alerts for the current page
  const currentAlerts = filteredAlerts.slice(
      currentPage * alertsPerPage,
      (currentPage + 1) * alertsPerPage
  );

  // Data for Graph
  const graphData = {
    labels: currentAlerts.map(
        (_, index) => `Alert ${index + 1} (Lat: ${_.latitude}, Lon: ${_.longitude})`
    ),
    datasets: [
      {
        label: "Temperature Deviations (°C)",
        data: currentAlerts.map((alert) => alert.deviation),
        backgroundColor: "rgba(54, 162, 235, 0.6)",
        borderColor: "rgba(54, 162, 235, 1)",
        borderWidth: 1,
      },
    ],
  };

  return (
      <div className="App">
        <header className="App-header">
          <h1>Real-Time Temperature Alerts</h1>

          {/* Search Bar */}
          <div className="SearchBar">
            <input
                type="text"
                placeholder="Search by any field (e.g., latitude)"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>

          {/* Graph: Visualizes current page alerts */}
          <div className="Graph">
            <h2>Temperature Deviations Graph</h2>
            {currentAlerts.length > 0 ? (
                <Bar data={graphData} />
            ) : (
                <p>No data to visualize</p>
            )}
          </div>

          {/* Alerts List with Pagination */}
          <div className="AlertsList">
            <h2>Paginated Alerts</h2>
            {currentAlerts.length > 0 ? (
                <table>
                  <thead>
                  <tr>
                    <th>Latitude</th>
                    <th>Longitude</th>
                    <th>Deviation (°C)</th>
                    <th>Climate Temperature (°C)</th>
                    <th>Weather Temperature (°C)</th>
                  </tr>
                  </thead>
                  <tbody>
                  {currentAlerts.map((alert, index) => (
                      <tr key={index}>
                        <td>{alert.latitude}</td>
                        <td>{alert.longitude}</td>
                        <td>{alert.deviation}</td>
                        <td>{alert.climateTemperature}</td>
                        <td>{alert.weatherTemperature}</td>
                      </tr>
                  ))}
                  </tbody>
                </table>
            ) : (
                <p>No alerts found!</p>
            )}

            {/* React Paginate */}
            <ReactPaginate
                previousLabel={"<<"}
                nextLabel={">>"}
                breakLabel={"..."}
                pageCount={Math.ceil(filteredAlerts.length / alertsPerPage)}
                marginPagesDisplayed={2}
                pageRangeDisplayed={3}
                onPageChange={handlePageClick}
                containerClassName={"pagination"}
                activeClassName={"active"}
            />
          </div>
        </header>
      </div>
  );
}

export default App;